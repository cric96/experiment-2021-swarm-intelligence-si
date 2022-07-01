package it.scafi.lib.clustering
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

import scala.language.existentials

/**
 * Common clustering algorithm abstractions. 
 * They will be used to define the concrete clustering implementation
 */
trait ClusteringAbstraction {
  self: AggregateProgram =>
  trait Clustering {
    // Abstract type
    /** Type for the input data used by the clustering algorithm to define if a node is inside or outside of a give cluster */
    type Input

    /** Type for the data associated with a cluster (e.g. the centroid, the average temperature, ...) */
    type ClusterData

    /** Type for collecting the data necessary to compute the Cluster Data (e.g. local position, local temperature, ...) */
    type LocalData

    /** Type for the key of a given cluster. It has to identify a cluster uniquely */
    type Key
    // Type alias
    type Cluster = Map[Key, ClusterData]
    // Data type definition
    /**
     * The result of the clustering process. 
     * It will contain the cluster founded (all) and possible the cluster merged (merged)
     */
    case class ClusterDivision(all: Cluster, merged: Cluster)

    /** Input definition: when evaluate to the leader node, it should return the centre of a cluster */
    def input: Input

    /** Local data definition: describe the data in which we are interested for define a cluster representation (it could be the same of input)*/
    def localData: LocalData

    /** Key definition: produce a key for the cluster when it is evaluated to a candidate node */
    def keyFactory: Key

    /** Metric definition: what is the metric used to compute the gradient from the cluster centre */
    def metric: Metric

    /** Candidate definition: a predicate that return True when the node should start a cluster expansion, false otherwise */
    def isCandidate: Boolean

    /** Define the counts used to mark a process as terminated (i.e., due to split brains or failures) */
    def lastWillCount: Int

    /**
     * Expand definition: describe how the input selected in the leader evolve during the gradient cast expansion*
     * @param input: input field that need that need to be changed
     * @return
     */
    def expand(input: Input): Input

    /**
     * Collect policy definition: describe how two local data should be combined
     * @param accumulator left side of the data
     * @param local new data that needs to be collected
     * @return the data merged
     */
    def collect(accumulator: LocalData, local: LocalData): LocalData

    /**
     * Finalization policy definition: describe how the leader produce the cluster data description from the local data collection 
     * @param clusterData the local data collected from nodes
     * @return the cluster data information from this leader (identify by key)
     */
    def finalization(clusterData: LocalData): ClusterData

    /**
     * In condition predicate: define if a certain node is inside or outside of a give cluster.
     * @param key the cluster key
     * @param data the input data expanded from the leader
     * @return true if the zone is inside the cluster false otherwise
     */
    def inCondition(key: Key, data: Input): Boolean
    def apply(): ClusterDivision

    /* utility used to create empty cluster result */
    protected[clustering] def emptyClusterDivision: ClusterDivision = ClusterDivision(emptyCluster, emptyCluster)
    protected def emptyCluster: Cluster = Map.empty[Key, ClusterData]
    /* utility for creating a clustering division with all == merged */
    protected def createCluster(clusterKey: Key, clusterData: ClusterData): ClusterDivision = {
      val clusters = Map(clusterKey -> clusterData)
      ClusterDivision(clusters, clusters)
    }
    /* Utility in data collection operating with Option[LocalData].
     * It return left or right if one of two is empty, otherwise it returns: for { l <- left; r <- right } yield (collect(l, r))
     * */
    protected def combineOption(left: Option[LocalData], right: Option[LocalData]): Option[LocalData] =
      (left, right) match {
        case (None, right @ Some(_)) => right
        case (left @ Some(_), None) => left
        case (Some(left), Some(right)) => Some(collect(left, right))
        case _ => None
      }
  }

  object Clustering {

    /**
     * Type extractor for Clustering algorithm
     * @tparam K Clustering.Key
     * @tparam I Clustering.Input
     * @tparam D Clustering.LocalData
     * @tparam C Clustering.ClusterData
     */
    type Aux[K, I, D, C] = Clustering {
      type Key = K
      type Input = I
      type LocalData = D
      type ClusterData = C
    }

    /**
     * Type extractor for Cluster
     * @tparam K Clustering.Key
     * @tparam C Clustering.ClusterData
     */
    type Cluster[K, C] = (Aux[K, _, _, C])#Cluster // type projection to get correct cluster type
    /**
     * Type extractor for cluster division
     * @tparam K Clustering.Key
     * @tparam C Clustering.ClusterData
     */
    type ClusteringDivision[K, C] = (Aux[K, _, _, C])#ClusterDivision // type projection to get correct cluster division
  }
}
