package it.unibo.alchemist.loader.`export`.extractors

import it.unibo.alchemist.loader.`export`.Extractor

import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava

trait CommonNames extends {
  self: Extractor[_] =>
  val scalaNames = (1 to 5).map(i => s"temperature-$i")
  override def getColumnNames: util.List[String] = scalaNames.asJava
}
