package it.unibo.alchemist.loader.`export`.extractors

import it.unibo.alchemist.loader.`export`.Extractor
import it.unibo.alchemist.model.implementations.nodes.SimpleNodeManager
import it.unibo.alchemist.model.interfaces
import it.unibo.alchemist.model.interfaces.{Environment, Reaction}

import java.util
import scala.jdk.CollectionConverters.{IteratorHasAsScala, MapHasAsJava}

/**
 * Extractor that evaluates the cluster count found by the algorithm
 */
class ClusterCount extends Extractor[Int] {
  override def getColumnNames: util.List[String] = util.Arrays.asList("clusters", "all-clusters")

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

  private def extract[T](environment: Environment[_, _], name: String): Iterator[T] = {
    environment.getNodes
      .stream()
      .iterator()
      .asScala
      .map(node => new SimpleNodeManager(node))
      .filter(_.has(name))
      .map(node => node.get[T](name))
  }
}
