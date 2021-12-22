package it.scafi
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
trait ProcessFix extends CustomSpawn {
  self: AggregateProgram =>
  override def runOnSharedKeysWithShare[K, A, R](process: K => (R, Boolean), params: Set[K]): Map[K, R] = {
    share(Map[K, R]())((loc, nbr) => {
      (includingSelf
        .unionHoodSet(nbr().keySet ++ params))
        .mapToValues(k => exportConditionally(process.apply(k)))
        .collectValues[R] { case (r, true) => r }
    })
  }
}
