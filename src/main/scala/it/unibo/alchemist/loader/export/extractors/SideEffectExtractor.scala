package it.unibo.alchemist.loader.`export`.extractors

import it.unibo.alchemist.loader.`export`.Extractor
import it.unibo.alchemist.model.implementation.layers.UpdatableBidimensionalGuassianLayer
import it.unibo.alchemist.model.interfaces
import it.unibo.alchemist.model.interfaces.{Environment, Position, Position2D, Reaction}
import org.apache.commons.math3.random.RandomGenerator

import java.util
import scala.jdk.CollectionConverters.{IteratorHasAsScala, MapHasAsJava, SeqHasAsJava}

/** a fake extractor used only for produce side effect */
class SideEffectExtractor[P <: Position2D[P]](
  val rand: RandomGenerator,
  val expandUntil: Long,
  val update: Double,
  val minRetraction: Double
) extends Extractor[Unit] {
  override def getColumnNames: util.List[String] = List.empty.asJava
  val tickUpdate = update / expandUntil
  override def extractData[T](
    environment: Environment[T, _],
    reaction: Reaction[T],
    time: interfaces.Time,
    l: Long
  ): util.Map[String, Unit] = {
    val unsafeEnv = environment.asInstanceOf[Environment[T, P]]
    val elements = unsafeEnv.getLayers.iterator().asScala.toList
    val toUpdate = elements.collect { case l: UpdatableBidimensionalGuassianLayer[_] => l }
    // sorry...
    for (layer <- toUpdate) {
      if (time.toDouble < expandUntil) {
        layer.deltaChangeSize(tickUpdate)
      } else if ((layer.sigmaX - tickUpdate) > minRetraction) {
        layer.deltaChangeSize(-tickUpdate)
      }
    }
    Map.empty[String, Unit].asJava
  }
}
