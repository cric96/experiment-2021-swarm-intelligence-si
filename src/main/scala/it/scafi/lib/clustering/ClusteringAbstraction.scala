package it.scafi.lib.clustering
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

import scala.language.existentials
trait ClusteringAbstraction {
  self: AggregateProgram =>
  trait Clustering {
    type Input
    type ClusterData
    type Key
    type Cluster = Map[Key, ClusterData]

    case class ClusterDivision(all: Cluster, merged: Cluster)

    def input: Input
    def localData: ClusterData
    def keyFactory: Key
    def metric: Metric
    def isCandidate: Boolean

    def expand(input: Input): Input
    def collect(accumulator: ClusterData, local: ClusterData): ClusterData
    def finalization(clusterData: ClusterData): ClusterData
    def inCondition(key: Key, data: Input): Boolean
    def apply(): ClusterDivision

    protected def emptyCluster: Cluster = Map.empty[Key, ClusterData]
    protected def emptyClusterDivision: ClusterDivision = ClusterDivision(emptyCluster, emptyCluster)
    protected def createCluster(clusterKey: Key, clusterData: ClusterData): ClusterDivision = {
      val clusters = Map(clusterKey -> clusterData)
      ClusterDivision(clusters, clusters)
    }
    protected def combineOption(left: Option[ClusterData], right: Option[ClusterData]): Option[ClusterData] =
      (left, right) match {
        case (None, right @ Some(_)) => right
        case (left @ Some(_), None) => left
        case (Some(left), Some(right)) => Some(collect(left, right))
        case _ => None
      }
  }

  object Clustering {
    type Aux[K, I, D] = Clustering {
      type Key = K
      type Input = I
      type ClusterData = D
    }

    type Cluster[K, D] = (Clustering {
      type Key = K
      type ClusterData = D
    })#Cluster

    type ClusteringDivision[K, D] = (Clustering {
      type Key = K
      type ClusterData = D
    })#ClusterDivision
  }
}
