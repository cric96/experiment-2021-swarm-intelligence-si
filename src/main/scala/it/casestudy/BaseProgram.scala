package it.casestudy
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

import scala.util.Try

class BaseProgram
    extends AggregateProgram
    with StandardSensors
    with ScafiAlchemistSupport
    with FieldUtils
    with Gradients
    with CustomSpawn
    with TimeUtils
    with StateManagement
    with ProcessDSL {
  implicit val precision: Precision = Precision(0.000001)
  // fix for sensing layers
  override def sense[A](name: String): A = {
    Try { vm.localSense[A](name) }.orElse { Try { senseEnvData[A](name) } }.get
  }

  case class ClusterStart(leaderId: ID, temperature: Double)
  case class ClusterInformation(center: ID, minTemperature: Double, hopCountDistance: Int)

  override def main(): Any = {
    val temperature: Double = sense[java.lang.Double]("temperature")
    val temperatureField = includingSelf.reifyField(nbr { temperature })
    val (minId, minimumTemperature) = temperatureField.minBy(_._2)

    val sameTemperature = temperatureField.filter { case (_, data) => minimumTemperature ~= data }
    val candidate = sameTemperature.size match {
      case candidates if candidates > 1 => sameTemperature.keys.min == mid()
      case _ => minId == mid()
    }
    node.put("temperaturePerceived", temperature)
    node.put("candidate", candidate)
    candidate

  }
}
