package it.unibo.alchemist.loader.`export`.extractors

import breeze.linalg
import breeze.linalg._
import it.unibo.alchemist.loader.`export`.Extractor
import it.unibo.alchemist.model.implementations.nodes.SimpleNodeManager
import it.unibo.alchemist.model.interfaces
import it.unibo.alchemist.model.interfaces.{Environment, Position, Reaction}

import java.util
import scala.collection.compat.immutable.ArraySeq
import scala.jdk.CollectionConverters.{IteratorHasAsScala, MapHasAsJava}

class ClusterMetrics extends Extractor[Double] {
  type Clusters = Map[Int, List[Vector[Double]]]

  override def getColumnNames: util.List[String] = util.Arrays.asList("silhouette", "dunnIndex")

  override def extractData[T](
    environment: Environment[T, _],
    reaction: Reaction[T],
    time: interfaces.Time,
    l: Long
  ): util.Map[String, Double] = {
    val nodesList = environment.getNodes.iterator().asScala.toList
    val unsafeEnv = environment.asInstanceOf[Environment[T, P forSome { type P <: Position[P] }]]
    val clusters = nodesList
      .map(node => (node, new SimpleNodeManager(node)))
      .filter { case (node, manager) => manager.has("clusters") }
      .map { case (node, manager) => (node, manager, manager.get[Set[Int]]("clusters")) }
      .flatMap { case (node, manager, clusters) => clusters.map(id => (id, node, manager)) }
      .groupBy { case (id, _, _) => id }
      .map { case (id, elements) =>
        id -> elements.map { case (_, node, manager) =>
          val point = unsafeEnv.getPosition(node)
          val data = point.getCoordinates :+ manager.get[Double]("temperaturePerceived")

          linalg.Vector.apply[Double](ArraySeq.unsafeWrapArray(data): _*)
        }
      }

    Map("silhouette" -> silhouette(clusters), "dunnIndex" -> dunnIndex(clusters)).asJava
  }

  // from https://en.wikipedia.org/wiki/Silhouette_(clustering)
  def silhouette(clusters: Clusters): Double = zeroIfEmptyOrElse(clusters) {
    def internal(target: Vector[Double], samples: List[Vector[Double]]): Double = {
      val distances = samples.map(sample => Math.sqrt(linalg.squaredDistance(target, sample)))
      distances.sum / distances.size
    }

    def external(target: Vector[Double], clusters: Map[Int, List[Vector[Double]]]): Double = {
      clusters.map { case (k, v) => internal(target, v) }.minOption.getOrElse(Double.PositiveInfinity)
    }

    val internalExternalFactors = for {
      (id, elements) <- clusters
      sample <- elements
    } yield (internal(sample, elements), external(sample, clusters.removed(id)))

    val result =
      internalExternalFactors.map { case (a, b) => (b - a) / math.max(a, b) }.sum / internalExternalFactors.size

    result
  }

  // from http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.1041.6282&rep=rep1&type=pdf
  def dunnIndex(clusters: Clusters): Double = zeroIfEmptyOrElse(clusters) {
    val defaultDenominator = 0.0
    val defaultNumerator = 0.0
    val centroids = clusters.map { case (id, values) =>
      val result: linalg.Vector[Double] = values.reduce(_ + _)
      id -> result / values.size.toDouble
    }
    val maxDistances = clusters.map { case (id, values) =>
      val cartesianProduct = for {
        left <- values
        right <- values
      } yield (left, right)

      cartesianProduct
        .map { case (left, right) => Math.sqrt(linalg.squaredDistance(left, right)) }
        .maxOption
        .getOrElse(defaultDenominator)
    }
    val centroidDistances = for {
      (id, centroid) <- centroids
      (_, otherCentroid) <- (centroids.removed(id))
    } yield (Math.sqrt(linalg.squaredDistance(centroid, otherCentroid)))

    centroidDistances.minOption.getOrElse(defaultNumerator) / maxDistances.maxOption.getOrElse(defaultDenominator)
  }

  def zeroIfEmptyOrElse(cluster: Clusters)(logic: => Double): Double = if (cluster.nonEmpty) {
    logic
  } else {
    Double.NaN
  }
}
