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
    val temperatureNeighbourField = includingSelf.reifyField(nbr { temperature })
    // find the minimum temperature value within the neighbourhood
    val (minId, minimumTemperature) = temperatureNeighbourField.minBy(_._2)
    // neighbourhood field composed by the node with the same temperature (~=)
    val sameTemperature = temperatureNeighbourField.filter { case (_, data) => minimumTemperature ~= data }
    /* CLUSTER CENTER CANDIDATE
     * CONDITIONS:
     * d.m() < d'.m()  OR
     * ( d.m() = d'.m() AND d.id() < d'.id() )
     */
    val candidate = sameTemperature.size match {
      // ( d.m() = d'.m() AND d.id() < d'.id() )
      case candidates if candidates > 1 => sameTemperature.keys.min == mid()
      // d.m() < d'.m()
      case _ => minId == mid()
    }
    // Start new process when a new local minimum is found
    val clusterStarter = mux(candidate) { Set(ClusterStart(mid(), temperature)) } { Set.empty[ClusterStart] }
    val clusters =
      branch(T(10) <= 0) { // TODO FIX => it helps to avoid initial cluster instantiation (indeed at the beginning, each node is a local minimum)
        temperatureCluster(clusterStarter, ClusterInput(temperature, threshold)) // process handling
      } {
        Map.empty
      }
    // A way to "merge" clusters, I am interested in the cluster with the lowest temperature.
    val coldestCluster = clusters.minByOption(
      _._1.temperature
    )
    node.put("temperaturePerceived", temperature)
    node.put("candidate", candidate)
    coldestCluster.foreach(cluster => node.put("clusterId", cluster._1.leaderId))
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
      case ClusterStart(leader, minTemperature) => { case ClusterInput(temperature, threshold) =>
        val distanceFromLeader = classicGradient(mid() == leader, () => 1).toInt
        val difference = temperature - minTemperature
        (ClusterInformation(distanceFromLeader), difference >= 0.0 && difference <= threshold)
      }
    }
    spawn[ClusterStart, ClusterInput, ClusterInformation](spawnLogic, start, input)
  }
}

object BaseProgram {
  case class ClusterStart(leaderId: ID, temperature: Double)
  case class ClusterInformation(hopCountDistance: Int)
  case class ClusterInput(temperaturePerceived: Double, threshold: Double)
}
