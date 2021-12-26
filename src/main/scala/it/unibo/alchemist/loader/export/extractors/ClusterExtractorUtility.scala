package it.unibo.alchemist.loader.`export`.extractors

import it.unibo.alchemist.loader.`export`.Extractor
import it.unibo.alchemist.model.implementations.nodes.SimpleNodeManager
import it.unibo.alchemist.model.interfaces.Environment

import scala.jdk.CollectionConverters.IteratorHasAsScala

trait ClusterExtractorUtility {
  self: Extractor[_] =>

  protected def extract[T](environment: Environment[_, _], name: String): Iterator[T] = {
    environment.getNodes
      .stream()
      .iterator()
      .asScala
      .map(node => new SimpleNodeManager(node))
      .filter(_.has(name))
      .map(node => node.get[T](name))
  }
}
