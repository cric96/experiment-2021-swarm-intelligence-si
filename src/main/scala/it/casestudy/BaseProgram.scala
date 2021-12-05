package it.casestudy
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

import scala.util.Try

class BaseProgram extends AggregateProgram with StandardSensors with ScafiAlchemistSupport {
  // fix for sensing layers
  override def sense[A](name: String): A = {
    Try { vm.localSense[A](name) }.orElse { Try { senseEnvData[A](name) } }.get
  }
  override def main(): Any = sense[java.lang.Double]("temperature")
}
