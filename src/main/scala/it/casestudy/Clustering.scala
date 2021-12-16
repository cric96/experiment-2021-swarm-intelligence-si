package it.casestudy
import it.casestudy.Clustering._
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
    with CustomSpawn
    with TimeUtils
    with StateManagement
    with ClusteringLib {
  implicit val precision: Precision = Precision(0.000001)
  private lazy val threshold = node.get[Double]("inClusterThr")
  private lazy val sameClusterThr = node.get[Double]("sameClusterThr")
  private lazy val waitingTime = node.get[Int]("waitingTime")

  override def main(): Any = {
    val temperature: Double = sense[java.lang.Double]("temperature")
    // "hysteresis" condition, waiting time before starting a process
    val candidate = branch(isCandidate()) { T(waitingTime) <= 0 } {
      false
    }

    val merged = cluster
      .input { ClusteringProcessInput(temperature, threshold, candidate) }
      .keyGenerator { ClusteringKey(mid())(temperature, timestamp()) }
      .process(key =>
        input => {
          val distanceFromLeader = classicGradient(mid() == key.leaderId, () => 1).toInt
          val clusterInformation =
            broadcast(mid() == key.leaderId, evaluateClusterInformation(distanceFromLeader, input.temperaturePerceived))
          ClusteringProcessOutput(distanceFromLeader, clusterInformation)
        }
      )
      .insideIf { key => input => _ => inCluster(input.temperaturePerceived, key.temperature, input.threshold) }
      .candidate { candidate }
      .mergeWhen(clusters => mergeCluster(clusters))
      .killWhen(clusters => watchDog(clusters, candidate))
      .overlap()
    // val merged = mergeCluster(clusters)
    // A way to "merge" clusters, I am interested in the cluster with the lowest temperature.
    val coldestCluster = merged.minByOption(
      _._1.temperature
    )
    // val coldestCluster = disjointedClusters(candidate, temperature, threshold)
    node.put("temperaturePerceived", temperature)
    node.put("candidate", candidate)
    node.put("clusters", merged.keySet.map(_.leaderId))
    // node.put("centroids", clusters.map(_._2.information.centroid))
    // node.put("fullClustersInfo", clusters)
    coldestCluster match {
      case Some((ClusteringKey(leader), _)) => node.put("clusterId", leader)
      case None if node.has("clusterId") => node.remove("clusterId")
      case _ =>
    }
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
  def watchDog(processes: Map[ClusteringKey, ClusteringProcessOutput], candidate: Boolean): Set[ClusteringKey] = {
    processes.filter { case (ClusteringKey(id), _) => mid() == id && !candidate }.keySet
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
    clusterInfo: Map[ClusteringKey, ClusteringProcessOutput]
  ): Map[ClusteringKey, ClusteringProcessOutput] = {
    clusterInfo
      .foldLeft(Map.empty[ClusteringKey, ClusteringProcessOutput]) { case (acc, (clusteringKey, data)) =>
        val sameCluster = acc.filter { case (_, currentData) =>
          currentData.information.centroid.distance(data.information.centroid) +- sameClusterThr
        }
        val toRemove = sameCluster.filter { case (currentClusterKey, _) =>
          currentClusterKey.leaderId > clusteringKey.leaderId
        }
        if (toRemove.nonEmpty || toRemove.isEmpty && acc.isEmpty) { (acc -- toRemove.keys) + (clusteringKey -> data) }
        else if (sameCluster.nonEmpty) { acc }
        else { acc + (clusteringKey -> data) }
      }
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
    difference >= 0.0 && difference <= threshold
  }
}

object Clustering {
  case class ClusteringKey(leaderId: ID)(val temperature: Double, val timestamp: Long)
  case class ClusteringProcessOutput(hopCountDistance: Int, information: ClusterInformation[Double])
  case class ClusteringProcessInput(
    temperaturePerceived: Double,
    threshold: Double,
    wasCandidate: Boolean = false,
    clusterToKill: Set[ClusteringKey] = Set.empty
  )
}
