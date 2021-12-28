package it.unibo.alchemist.loader.`export`.extractors

import it.unibo.alchemist.loader.`export`.Extractor
import it.unibo.alchemist.model.interfaces
import it.unibo.alchemist.model.interfaces.{Environment, Reaction}

import java.util
import scala.jdk.CollectionConverters.MapHasAsJava

/**
 * Extractor that evaluates the cluster count found by the algorithm
 */
class ClusterCount extends Extractor[Int] with ClusterExtractorUtility {
  override def getColumnNames: util.List[String] = util.Arrays.asList("cluster", "all-clusters")

  override def extractData[T](
    environment: Environment[T, _],
    reaction: Reaction[T],
    time: interfaces.Time,
    l: Long
  ): util.Map[String, Int] = {
    val clusters = extract[Set[Int]](environment, "clusters")
      .foldLeft(Set.empty[Int])((acc, data) => acc ++ data)

    val overlapped = extract[Set[Int]](environment, "allClusters")
      .foldLeft(Set.empty[Int])((acc, data) => acc ++ data)
    Map("cluster" -> clusters.size, "all-clusters" -> overlapped.size).asJava
  }
}
