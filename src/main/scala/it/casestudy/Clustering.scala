package it.casestudy

import it.casestudy.Clustering._
import it.scafi.lib.BlocksWithShare
import it.scafi.lib.clustering.ClusteringLib
import it.scafi.{MovementUtils, ProcessFix}
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

import scala.jdk.CollectionConverters.ListHasAsScala
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
    with BlocksWithShare
    with ClusteringLib {
  // Context
  implicit val precision: Precision = Precision(0.000001)
  // Alchemist environment variables
  private lazy val threshold = node.get[Double](Molecules.inClusterThr)
  private lazy val sameClusterThr = node.get[Double](Molecules.sameClusterThr)
  private lazy val waitingTime = node.get[Int](Molecules.waitingTime)
  private lazy val zoneSize = node.get[Double](Molecules.exploreArea)
  // Constants
  private val maxFollowDirectionTime = 1000
  private val reachTargetThr = 0.1
  private lazy val zoneCenter = (currentPosition().x, currentPosition().y)
  private lazy val zone = RectangularZone(zoneCenter, zoneSize * 2, zoneSize * 2)
  override def main(): Any = {
    val temperature: Double = sense[java.lang.Double](Molecules.temperature)
    // waiting time before starting a process
    val candidate = branch(isCandidate()) { T(waitingTime) <= 0 } { false }
    val clusters = rep(emptyClusterDivision[ClusteringKey, ClusterInformation[Double]]) { feedbackResult =>
      cluster
        .input { ClusteringProcessInput(temperature, threshold, candidate) }
        .key { ClusteringKey(mid())(temperature) }
        .shareInput
        .localInformation(Map(mid() -> SpatialData(currentPosition(), temperature)))
        .collect(_ ++ _)
        .finalize(data => {
          val minPoint = data.values.min
          val maxPoint = data.values.max
          val average = data.values.reduce(_ + _) / data.values.size
          ClusterInformation(minPoint, maxPoint, average)
        })
        .candidate(candidate)
        .inIff { (_, input) => inCluster(temperature, input.temperaturePerceived, threshold) }
        .merge((key, clusters) => mergeCluster(key, clusters))
        .watchDog { watchDog(feedbackResult.merged, candidate) }
        .overlap()
    }
    node.put(Molecules.temperaturePerceived, temperature)
    node.put(Molecules.candidate, candidate)
    node.put(Molecules.clusters, clusters.merged.keySet.map(_.leaderId))
    // println(clusters.all.keySet.map(_.leaderId))
    node.put(Molecules.allClusters, clusters.all.keySet.map(_.leaderId))
    movementLogic(clusters.merged)
    candidate
  }
  // fix for sensing layers
  override def sense[A](name: String): A = {
    Try { vm.localSense[A](name) }.orElse { Try { senseEnvData[A](name) } }.get
  }

  def isCandidate(): Boolean = {
    val temperature: Double = sense[java.lang.Double](Molecules.temperature)
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
      case _ if temperatureNeighbourField.size <= 1 => false // I am alone, no process will be spawn
      // ( d.m() = d'.m() AND d.id() < d'.id() )
      case candidates if candidates > 1 => sameTemperature.keys.min == mid()
      // d.m() < d'.m()
      case _ => minId == mid()
    }
  }

  /**
   * extract cluster information from a given potential field
   * @param potential the distance (hop count) from the central cluster
   * @param temperature the local temperature
   * @return (min point, max point, centroid) of the cluster
   */
  def evaluateClusterInformation(potential: Int, temperature: Double): ClusterInformation[Double] = {
    val data = {
      CWithShare[Int, Map[ID, SpatialData[Double]]](
        potential,
        _ ++ _,
        Map(mid() -> SpatialData(currentPosition(), temperature)),
        Map.empty
      )
    }
    val minPoint = data.values.min
    val maxPoint = data.values.max
    val average = data.values.reduce(_ + _) / data.values.size
    ClusterInformation(minPoint, maxPoint, average)
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

  def isSameCluster[V](reference: ClusterInformation[V], other: ClusterInformation[V]): Boolean = {
    reference.centroid.distance(other.centroid) +- sameClusterThr
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

  def movementLogic(clusters: Clustering.Cluster[ClusteringKey, ClusterInformation[Double]]): Unit = {
    if (clusters.keySet.map(_.leaderId).contains(mid())) {
      node.put(Molecules.target, currentPosition())
    } else {

      node.put(Molecules.target, explore(zone, maxFollowDirectionTime, reachTargetThr))
    }
  }
}

object Clustering {
  case class ClusteringKey(leaderId: ID)(val startingTemperature: Double)
  case class ClusteringProcessOutput(
    hopCountDistance: Int,
    leaderTemperature: Double,
    information: ClusterInformation[Double]
  )
  case class ClusteringProcessInput(
    temperaturePerceived: Double,
    threshold: Double,
    wasCandidate: Boolean = false
  )
  case class ClusterInformation[V: Numeric](
    minPoint: SpatialData[V],
    maxPoint: SpatialData[V],
    centroid: SpatialData[V]
  )

  object Molecules {
    val temperature = "temperature"
    val target = "target"
    val clusters = "clusters"
    val allClusters = "allClusters"
    val temperaturePerceived = "temperaturePerceived"
    val candidate = "candidate"
    val inClusterThr = "inClusterThr"
    val sameClusterThr = "sameClusterThr"
    val exploreArea = "exploreArea"
    val waitingTime = "waitingTime"
  }
}
