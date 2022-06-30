package it.scafi.lib
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
import Builtins._
import it.scafi.ProcessFix
trait BlocksWithShare {
  self: AggregateProgram with BlockC with StandardSensors with ProcessFix =>
  def CWithShare[P: Bounded, V](potential: P, acc: (V, V) => V, local: V, Null: V): V =
    share(local) { (_, nbrv) =>
      acc(
        local,
        foldhood(Null)(acc) {
          mux(nbr(findParent(potential)) == mid()) { nbrv() } { nbr(Null) }
        }
      )
    }

  def GWithShare[V](source: Boolean, field: V, acc: V => V, metric: () => Double): V =
    share((Double.MaxValue, field)) { case ((dist, value), nbrvalues) =>
      mux(source) {
        (0.0, field)
      } {
        excludingSelf
          .minHoodSelector(nbrvalues()._1 + metric())((nbrvalues()._1 + metric() + metric(), acc(nbrvalues()._2)))
          .getOrElse((Double.PositiveInfinity, field))
      }
    }._2

  def classicGradientWithShare(source: Boolean, metric: () => Double = nbrRange): Double =
    share(Double.PositiveInfinity) { case (_, nbrg) =>
      mux(source) {
        0.0
      } {
        minHoodPlus(nbrg() + metric())
      }
    }
}
