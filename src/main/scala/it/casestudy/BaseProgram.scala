package it.casestudy
import it.casestudy.BaseProgram._
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

import scala.util.Try

class BaseProgram
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
  private val threshold = 1
  private val sameClusterThr = 0.1
  private val waitingTime = 5

  override def main(): Any = {
    val temperature: Double = sense[java.lang.Double]("temperature")
    // "hysteresis" condition, waiting time before starting a process
    val candidate = branch(isCandidate()) { T(waitingTime) <= 0 } {
      false
    }
    // Start new process when a new local minimum is found
    val clusterStarter = mux(candidate) { Set(ClusteringKey(mid(), temperature, timestamp())) } {
      Set.empty[ClusteringKey]
    }
    val clusters =
      temperatureCluster(clusterStarter, ClusteringProcessInput(temperature, threshold, candidate)) // process handling

    val merged = mergeCluster(clusters)
    // A way to "merge" clusters, I am interested in the cluster with the lowest temperature.
    val coldestCluster = merged.minByOption(
      _._1.temperature
    )
    // val coldestCluster = disjointedClusters(candidate, temperature, threshold)
    node.put("temperaturePerceived", temperature)
    node.put("candidate", candidate)
    node.put("clusters", merged.keySet.map(_.leaderId))
    node.put("centroids", clusters.map(_._2.information.centroid))
    node.put("fullClustersInfo", clusters)
    coldestCluster match {
      case Some((ClusteringKey(leader, _, _), _)) => node.put("clusterId", leader)
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
  def watchDog(processes: Map[ClusteringKey, ClusteringProcessOutput]): Set[ClusteringKey] = {
    val processesKey = processes.keys.groupBy(_.leaderId).values.flatten.filter(_.leaderId == mid())
    val toKeepAlive = processesKey.maxByOption { case ClusteringKey(_, _, timestamp) => timestamp }
    val toKill = toKeepAlive match {
      case Some(ClusteringKey(_, _, timestamp)) =>
        processesKey.filter { case ClusteringKey(_, _, otherProcess: Long) => otherProcess < timestamp }
      case _ => Set.empty
    }
    toKill.toSet
  }

  /*
   * uses sspawn for handling leader changes ==> handle leader changes
   * It creates temperature cluster following this condition:
   *  0 <= d'.m() - d.m() <= w
   * where d' is the current node where the process is evaluated and d is the cluster center.
   */
  def temperatureCluster(
    start: Set[ClusteringKey],
    input: ClusteringProcessInput
  ): Map[ClusteringKey, ClusteringProcessOutput] = {
    val spawnLogic: ClusteringKey => ClusteringProcessInput => POut[Option[ClusteringProcessOutput]] = {
      case cluster @ ClusteringKey(leader, minTemperature, _) => {
        case ClusteringProcessInput(temperature, threshold, wasCandidate, toKill) =>
          // this condition is used to change leader.
          // if it is leader (leader == mid()) and it is not a candidate anymore, it close the process.
          mux(leader == mid() && (!wasCandidate || toKill.contains(cluster))) {
            POut(Option.empty[ClusteringProcessOutput], SpawnInterface.Terminated)
          } {
            val distanceFromLeader = classicGradient(mid() == leader, () => 1).toInt
            val status = if (inCluster(temperature, minTemperature, threshold)) { SpawnInterface.Output }
            else { SpawnInterface.External }
            val clusterInformation =
              broadcast(mid() == leader, evaluateClusterInformation(distanceFromLeader, temperature))
            POut(Some(ClusteringProcessOutput(distanceFromLeader, clusterInformation)), status)
          }
      }
    }
    rep(Map.empty[ClusteringKey, ClusteringProcessOutput]) { oldMap =>
      val toKill = watchDog(oldMap)
      sspawn2(spawnLogic, start, input.copy(clusterToKill = toKill)).collect { case (k, Some(v)) => k -> v }
    } // process handling
  }

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

  def inCluster(myTemperature: Double, leaderTemperature: Double, threshold: Double): Boolean = {
    val difference = myTemperature - leaderTemperature
    difference >= 0.0 && difference <= threshold
  }

  // example of fluent disjoint cluster API
  def disjointedClusters(candidate: Boolean, temperature: Double, threshold: Double): Option[ID] = {
    cluster.disjoint
      .candidate(candidate)
      .broadcast(temperature)
      .join(leaderTemperature => inCluster(temperature, leaderTemperature, threshold))
      .start()
  }

  // example of a complex disjoint cluster API
  def clusterOnNodeNumber(candidate: Boolean, howMany: Int): Option[ID] = {
    cluster.disjoint
      .candidate(candidate)
      .broadcast(0)
      .accumulate(_ + 1)
      .join(potential => {
        val ids = C[Int, Set[(Double, Int)]](potential, _ ++ _, Set((potential, mid())), Set.empty)
        val acceptedIds = mux(candidate) { ids.toList.sortBy(_._1).take(howMany).toSet } { Set.empty }
        val clusterId = broadcast(candidate, acceptedIds)
        clusterId.map(_._2).contains(mid())
      })
      .start()
  }

}

object BaseProgram {
  case class ClusteringKey(leaderId: ID, temperature: Double, timestamp: Long) {

    def canEqual(other: Any): Boolean = other.isInstanceOf[ClusteringKey]

    override def equals(other: Any): Boolean = other match {
      case that: ClusteringKey =>
        (that.canEqual(this)) &&
          leaderId == that.leaderId &&
          temperature == that.temperature
      case _ => false
    }

    override def hashCode(): Int = {
      val state = Seq(leaderId, temperature)
      state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
    }
  }
  case class ClusteringProcessOutput(hopCountDistance: Int, information: ClusterInformation[Double])
  case class ClusteringProcessInput(
    temperaturePerceived: Double,
    threshold: Double,
    wasCandidate: Boolean = false,
    clusterToKill: Set[ClusteringKey] = Set.empty
  )
}
