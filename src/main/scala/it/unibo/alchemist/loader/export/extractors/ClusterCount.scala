package it.unibo.alchemist.loader.`export`.extractors

import it.unibo.alchemist.loader.`export`.Extractor
import it.unibo.alchemist.model.implementations.nodes.SimpleNodeManager
import it.unibo.alchemist.model.interfaces
import it.unibo.alchemist.model.interfaces.{Environment, Reaction}

import java.util
import scala.jdk.CollectionConverters.{IteratorHasAsScala, MapHasAsJava}

class ClusterCount() extends Extractor[Int] {
  override def getColumnNames: util.List[String] = util.Arrays.asList("clusters")

  override def extractData[T](
    environment: Environment[T, _],
    reaction: Reaction[T],
    time: interfaces.Time,
    l: Long
  ): util.Map[String, Int] = {
    val clusters = environment.getNodes
      .stream()
      .iterator()
      .asScala
      .map(node => new SimpleNodeManager[T](node))
      .filter(_.has("clusters"))
      .map(node => node.get[Set[Int]]("clusters"))
      .foldLeft(Set.empty[Int])((acc, data) => acc ++ data)
    Map("cluster" -> clusters.size).asJava
  }
}
