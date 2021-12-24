package it.scafi.lib.clustering
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

import scala.language.existentials
trait ClusteringAbstraction {
  self: AggregateProgram =>
  trait Clustering {
    type Input
    type ClusterData
    type LocalData
    type Key
    type Cluster = Map[Key, ClusterData]
    case class ClusterDivision(all: Cluster, merged: Cluster)

    def input: Input
    def localData: LocalData
    def keyFactory: Key
    def metric: Metric
    def isCandidate: Boolean

    def expand(input: Input): Input
    def collect(accumulator: LocalData, local: LocalData): LocalData
    def finalization(clusterData: LocalData): ClusterData
    def inCondition(key: Key, data: Input): Boolean
    def apply(): ClusterDivision

    protected[clustering] def emptyClusterDivision: ClusterDivision = ClusterDivision(emptyCluster, emptyCluster)
    protected def emptyCluster: Cluster = Map.empty[Key, ClusterData]
    protected def createCluster(clusterKey: Key, clusterData: ClusterData): ClusterDivision = {
      val clusters = Map(clusterKey -> clusterData)
      ClusterDivision(clusters, clusters)
    }
    protected def combineOption(left: Option[LocalData], right: Option[LocalData]): Option[LocalData] =
      (left, right) match {
        case (None, right @ Some(_)) => right
        case (left @ Some(_), None) => left
        case (Some(left), Some(right)) => Some(collect(left, right))
        case _ => None
      }
  }

  object Clustering {
    type Aux[K, I, D, C] = Clustering {
      type Key = K
      type Input = I
      type LocalData = D
      type ClusterData = C
    }

    type Cluster[K, C] = (Clustering {
      type Key = K
      type ClusterData = C
    })#Cluster

    type ClusteringDivision[K, C] = (Clustering {
      type Key = K
      type ClusterData = C
    })#ClusterDivision
  }
}
