package it.casestudy

import it.casestudy.Clustering._
import it.scafi.lib.clustering.ClusteringLib

import it.scafi.{MovementUtils, ProcessFix}
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

import scala.util.Try

class Clustering
    extends AggregateProgram
    with StandardSensors
    with ScafiAlchemistSupport
    with FieldUtils
    with Gradients
    with BlockG
    with BlockC
    with ProcessFix
    with TimeUtils
    with StateManagement
    with MovementUtils
    with ClusteringLib {
  // Context
  implicit val precision: Precision = Precision(0.000001)
  // Alchemist environment variables
  private lazy val threshold = node.get[Double]("inClusterThr")
  private lazy val sameClusterThr = node.get[Double]("sameClusterThr")
  private lazy val waitingTime = node.get[Int]("waitingTime")
  // Constants
  private val maxFollowDirectionTime = 100
  private val reachTargetThr = 0.01
  private val zoneSize = 5
  private val zoneCenter = (0.0, 0.0)

  override def main(): Any = {
    val temperature: Double = sense[java.lang.Double](Molecules.temperature)
    // waiting time before starting a process
    val candidate = branch(isCandidate()) { T(waitingTime) <= 0 } {
      false
    }
    val clusters = cluster
      .input { ClusteringProcessInput(temperature, threshold, candidate) }
      .key { ClusteringKey(mid())(temperature, timestamp()) }
      .shareInput
      .localInformation(Map(mid() -> ClusterData(currentPosition(), temperature)))
      .collect(_ ++ _)
      .finalize(data => {
        val minPoint = data.values.min
        val maxPoint = data.values.max
        val average = data.values.reduce(_ + _) / data.values.size
        ClusterInformation(minPoint, maxPoint, average)
      })
      .candidate(candidate)
      .inIff { (key, _) => inCluster(temperature, key.startingTemperature, threshold) }
      .merge((key, clusters) => mergeCluster(key, clusters))
      .watchDog { clusters => watchDog(clusters.merged, candidate) }
      .overlap()
    // val coldestCluster = disjointedClusters(candidate, temperature, threshold)
    node.put(Molecules.temperaturePerceived, temperature)
    node.put(Molecules.candidate, candidate)
    node.put(Molecules.clusters, clusters.merged.keySet.map(_.leaderId))
    // println(clusters.all.keySet.map(_.leaderId))
    node.put("allClusters", clusters.all.keySet.map(_.leaderId))
    movementLogic(clusters.merged)
    candidate
  }
  // fix for sensing layers
  override def sense[A](name: String): A = {
    Try { vm.localSense[A](name) }.orElse { Try { senseEnvData[A](name) } }.get
  }

  def isCandidate(): Boolean = {
    val temperature: Double = sense[java.lang.Double]("temperature")
    val temperatureNeighbourField = includingSelf.reifyField(nbr { temperature })
    // find the minimum temperature value within the neighbourhood
    val (minId, minimumTemperature) = temperatureNeighbourField.minBy(_._2)
    // neighbourhood field composed by nodes with the same temperature (~=)
    val sameTemperature = temperatureNeighbourField.filter { case (_, data) => minimumTemperature ~= data }
    /* CLUSTER CENTER CANDIDATE
     * CONDITIONS:
     * d.m() < d'.m()  OR
     * ( d.m() = d'.m() AND d.id() < d'.id() )
     */
    sameTemperature.size match {
      // ( d.m() = d'.m() AND d.id() < d'.id() )
      case candidates if candidates > 1 => sameTemperature.keys.min == mid()
      // d.m() < d'.m()
      case _ => minId == mid()
    }
  }

  /**
   * check what processes need to be killed
   * @param processes the process perceived from a node
   * @return the set of processes that will be killed
   */
  def watchDog(processes: Map[ClusteringKey, ClusterInformation[Double]], candidate: Boolean): Set[ClusteringKey] = {
    val killProcess = !candidate // branch(!candidate) { T(0) <= 0 } { false }
    processes.filter { case (ClusteringKey(id), _) => mid() == id && killProcess }.keySet
  }

  /**
   * extract cluster information from a given potential field
   * @param potential the distance (hop count) from the central cluster
   * @param temperature the local temperature
   * @return (min point, max point, centroid) of the cluster
   */
  def evaluateClusterInformation(potential: Int, temperature: Double): ClusterInformation[Double] = {
    val data = {
      C[Int, Map[ID, ClusterData[Double]]](
        potential,
        _ ++ _,
        Map(mid() -> ClusterData(currentPosition(), temperature)),
        Map.empty
      )
    }
    val minPoint = data.values.min
    val maxPoint = data.values.max
    val average = data.values.reduce(_ + _) / data.values.size
    ClusterInformation(minPoint, maxPoint, average)
  }

  /** 
   * Merge policy: define if two (or more) clusters are the same.
   * In this case I used the distance from the centroid.
   * */
  def mergeCluster(
    reference: ClusteringKey,
    clusterInfo: Map[ClusteringKey, ClusterInformation[Double]]
  ): (ClusteringKey, ClusterInformation[Double]) = {
    val referenceData = clusterInfo(reference)
    val sameClusters = clusterInfo.filter { case (_, currentData) => isSameCluster(currentData, referenceData) }
    sameClusters.minBy(_._1.leaderId)
  }

  /**
   * tell if a node is inside the cluster or outside.
   * @param myTemperature the local temperature
   * @param leaderTemperature the temperature of the leader that had start the cluster process
   * @param threshold the threshould used to consider the node in/out-side the cluster
   * @return True if the node is inside, False otherwise.
   */
  def inCluster(myTemperature: Double, leaderTemperature: Double, threshold: Double): Boolean = {
    val difference = myTemperature - leaderTemperature
    // Math.abs(difference) <= threshold
    difference >= 0.0 && difference <= threshold
  }

  def isSameCluster[V](reference: ClusterInformation[V], other: ClusterInformation[V]): Boolean = {
    reference.centroid.distance(other.centroid) +- sameClusterThr
    // (reference.minPoint.distance(other.minPoint)) +- sameClusterThr ||
    // (reference.maxPoint.distance(other.maxPoint)) +- sameClusterThr
  }

  def movementLogic(clusters: Clustering.Cluster[ClusteringKey, ClusterInformation[Double]]): Unit = {
    if (clusters.keySet.map(_.leaderId).contains(mid())) {
      node.put(Molecules.target, currentPosition())
    } else {
      node.put(Molecules.target, explore(CircularZone(zoneCenter, zoneSize), maxFollowDirectionTime, reachTargetThr))
    }
  }
}

object Clustering {
  case class ClusteringKey(leaderId: ID)(val startingTemperature: Double, val timestamp: Long)
  case class ClusteringProcessOutput(
    hopCountDistance: Int,
    leaderTemperature: Double,
    information: ClusterInformation[Double]
  )
  case class ClusteringProcessInput(
    temperaturePerceived: Double,
    threshold: Double,
    wasCandidate: Boolean = false,
    clusterToKill: Set[ClusteringKey] = Set.empty
  )

  object Molecules {
    val temperature = "temperature"
    val target = "target"
    val clusters = "clusters"
    val temperaturePerceived = "temperaturePerceived"
    val candidate = "candidate"
  }
}
