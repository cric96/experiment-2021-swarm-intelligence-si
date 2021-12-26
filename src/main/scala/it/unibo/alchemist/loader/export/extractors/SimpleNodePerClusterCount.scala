package it.unibo.alchemist.loader.`export`.extractors

import it.unibo.alchemist.loader.`export`.Extractor
import it.unibo.alchemist.model.interfaces
import it.unibo.alchemist.model.interfaces.{Environment, Reaction}

import java.util
import scala.collection.immutable.TreeMap
import scala.jdk.CollectionConverters.MapHasAsJava

/**
 * Utility used for one direction field. 
 * Count how many node belong to the cluster in times
 */
class SimpleNodePerClusterCount extends Extractor[Int] with CommonNames with ClusterExtractorUtility {
  private val rest = scalaNames.tail.map(name => name -> 0).toList
  override def extractData[T](
    environment: Environment[T, _],
    reaction: Reaction[T],
    time: interfaces.Time,
    l: Long
  ): util.Map[String, Int] = {
    val clusters = extract[Set[Int]](environment, "clusters").count(_.nonEmpty)
    val result = (scalaNames.head -> clusters) :: rest
    TreeMap(result: _*).asJava
  }
}
