package it.casestudy
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
import it.casestudy.BaseProgram._

import scala.util.Try

class BaseProgram
    extends AggregateProgram
    with StandardSensors
    with ScafiAlchemistSupport
    with FieldUtils
    with Gradients
    with CustomSpawn
    with TimeUtils
    with StateManagement {
  implicit val precision: Precision = Precision(0.000001)
  private val threshold = 1

  override def main(): Any = {
    val temperature: Double = sense[java.lang.Double]("temperature")
    val candidate = isCandidate()
    // Start new process when a new local minimum is found
    val clusterStarter = mux(candidate) { Set(ClusterStart(mid(), temperature)) } { Set.empty[ClusterStart] }
    val clusters =
      branch(T(10) <= 0) { // TODO FIX => it helps to avoid initial cluster instantiation (indeed at the beginning, each node is a local minimum)
        temperatureCluster2(clusterStarter, ClusterInput(temperature, threshold, candidate)) // process handling
      } {
        Map.empty
      }
    // A way to "merge" clusters, I am interested in the cluster with the lowest temperature.
    val coldestCluster = clusters.minByOption(
      _._1.temperature
    )
    node.put("temperaturePerceived", temperature)
    node.put("candidate", candidate)
    node.put("clusters", clusters.keys.mkString(","))
    coldestCluster match {
      case Some((ClusterStart(leader, _), _)) => node.put("clusterId", leader)
      case None if node.has("clusterId") => node.remove("clusterId")
      case _ =>
    }
    candidate
  }
  // fix for sensing layers
  override def sense[A](name: String): A = {
    Try { vm.localSense[A](name) }.orElse { Try { senseEnvData[A](name) } }.get
  }
  /*
   * It creates temperature cluster following this condition:
   *  0 <= d'.m() - d.m() <= w
   * where d' is the current node where the process is evaluated and d is the cluster center.
   */
  def temperatureCluster(start: Set[ClusterStart], input: ClusterInput): Map[ClusterStart, ClusterInformation] = {
    val spawnLogic: ClusterStart => ClusterInput => (ClusterInformation, Boolean) = {
      case ClusterStart(leader, minTemperature) => { case ClusterInput(temperature, threshold, _) =>
        val distanceFromLeader = classicGradient(mid() == leader, () => 1).toInt
        val difference = temperature - minTemperature
        (ClusterInformation(distanceFromLeader), difference >= 0.0 && difference <= threshold)
      }
    }
    spawn[ClusterStart, ClusterInput, ClusterInformation](spawnLogic, start, input)
  }
  /*
    uses sspawn for handling leader changes ==> handle leader changes
   */
  def temperatureCluster2(
    start: Set[ClusterStart],
    input: ClusterInput
  ): Map[ClusterStart, ClusterInformation] = {
    val spawnLogic: ClusterStart => ClusterInput => POut[ClusterInformation] = {
      case ClusterStart(leader, minTemperature) => { case ClusterInput(temperature, threshold, wasCandidate) =>
        // this condition is used to change leader.
        // if it is leader (leader == mid()) and it is not a candidate anymore, it close the process.
        mux(!wasCandidate && leader == mid()) {
          POut(ClusterInformation(-1), SpawnInterface.Terminated)
        } {
          val distanceFromLeader = classicGradient(mid() == leader, () => 1).toInt
          val difference = temperature - minTemperature
          val status = if (difference >= 0.0 && difference <= threshold) { SpawnInterface.Output }
          else { SpawnInterface.External }
          POut(ClusterInformation(distanceFromLeader), status)
        }
      }
    }
    sspawn2(spawnLogic, start, input)
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

}

object BaseProgram {
  case class ClusterStart(leaderId: ID, temperature: Double)
  case class ClusterInformation(hopCountDistance: Int)
  case class ClusterInput(temperaturePerceived: Double, threshold: Double, wasCandidate: Boolean = false)
}
