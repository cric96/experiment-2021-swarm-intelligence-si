package it.scafi

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist.{ScafiAlchemistSupport, _}

import scala.util.Try
trait SenseLayers {
  self: AggregateProgram with ScafiAlchemistSupport =>
  // fix for sensing layers, allow to perceive both sensor and layers
  override def sense[A](name: String): A = {
    Try { vm.localSense[A](name) }.orElse { Try { senseEnvData[A](name) } }.get
  }
}
