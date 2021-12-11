package it.casestudy

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
// TODO move the implementations details here and expose a clear API for clusters formation
trait ClusteringLib {
  self: AggregateProgram with StandardSensors with BlockG with CustomSpawn =>

  /**
   * Creates disjoint clusters identified by the ID of the candidate node
   * @param clusterCandidate a Boolean field that defines if a node is a cluster candidate (i.e. the center of the cluster) or not
   * @param clusterField the data necessary to build the cluster (i.e. a temperature field, ...)
   * @param fieldAccumulator define how the candidate value spread into the other nodes (you can use identity)
   * @param inCondition define the condition to join in the cluster
   * @param metric the metric used for the gradient cast
   * @tparam V the field data type
   * @return Option(clusterID) if the node participate to a cluster, None otherwise
   */
  def disjointCluster[V](
    clusterCandidate: Boolean,
    clusterField: V,
    fieldAccumulator: V => V,
    inCondition: V => Boolean,
    metric: Metric
  ): Option[ID] = {
    val (clusterID, value) =
      G[(ID, V)](clusterCandidate, (mid(), clusterField), data => (data._1, fieldAccumulator(data._2)), metric)
    mux(inCondition(value)) {
      Option(clusterID)
    } {
      Option.empty[ID]
    }
  }

  /**
   * Creates cluster (overlapped) identified by the ID of the candidate node
   * @param clusterCandidate a Boolean field that defines if a node is a cluster candidate (i.e. the center of the cluster) or nnot
   * @param clusterField the data necessary to build the cluster (i.e. a temperature field, the potential, ...)
   * @param inCondition define the condition to join in to a particular cluster (id is the cluster ID)
   * @param closeCondition define when a cluster is no longer active (e.g. some underline distribution condition are changed)
   * @tparam V the field type
   * @return a Map of ID -> V where ID is the cluster ID and V is the value of the central node
   */
  def cluster[V](
    clusterCandidate: Boolean,
    clusterField: V,
    inCondition: ID => V => Boolean,
    closeCondition: ID => V => Boolean
  ): Map[ID, V] = {
    val spawnLogic: ((ID, V)) => Unit => POut[V] = { case (id, value) =>
      _ => {
        // this condition is used to change leader.
        // if it is leader (leader == mid()) and it is not a candidate anymore, it close the process.
        mux(closeCondition(id)(value)) {
          POut(value, SpawnInterface.Terminated)
        } {
          val status = if (inCondition(id)(value)) { SpawnInterface.Output }
          else { SpawnInterface.External }
          POut(value, status)
        }
      }
    }
    sspawn2[(ID, V), Unit, V](
      spawnLogic,
      mux(clusterCandidate) { Set((mid(), clusterField)) } { Set.empty[(ID, V)] },
      args = {}
    )
      .map { case ((k, _), v) => k -> v }
  }

  /**
   * Facades for cluster identification
   */
  object cluster {

    /**
     * overlapped cluster identification
     */
    object overlapped {}

    /**
     * disjoint cluster identification
     */
    object disjoint {

      /**
       * define the candidate condition
       * @param condition a Boolean field that is true where the node is a candidate, false otherwise
       * @return LeaderSelected (step builder)
       */
      def candidate(condition: Boolean): CandidateSelected = CandidateSelected(condition)

      /**
       * Root interface for disjoint clusters builder
       */
      trait ClusterStepBuilder {}

      /**
       * Phase after the candidate selection, then the user should define the field type
       * @param leaderCondition @see [cluster.disjoint.candidate]
       */
      case class CandidateSelected(leaderCondition: Boolean) extends ClusterStepBuilder {
        def broadcast[V](field: V): FieldSelected[V] = FieldSelected(this, field, f => f)
      }

      /**
       * Phase after field selection, then the user should define the join condition (or he can change the accumulation strategy)
       * @param leaderSelected @see [cluster.disjoint.candidate]
       * @param field @see [cluster.disjoint.CandidateSelected]
       * @param policy accumulation policy, by default it is the identity (from CandidateSelected)
       * @tparam V the field type
       */
      case class FieldSelected[V](leaderSelected: CandidateSelected, field: V, policy: V => V)
          extends ClusterStepBuilder {
        def accumulate(f: V => V): FieldSelected[V] = copy(policy = f)
        def join(condition: V => Boolean): JoinSelected[V] = JoinSelected(leaderSelected, this, condition)
      }

      /**
       * Phase after join policy selection, then the user can start the cluster program (with start()) or choose the metric for the gradient cast
       * @param leaderSelected @see [cluster.disjoint.candidate]
       * @param fieldSelected @see [cluster.disjoint.CandidateSelected]
       * @param inCondition @see [cluster.disjoint.FieldSelected]
       * @tparam V the field type
       */
      case class JoinSelected[V](
        leaderSelected: CandidateSelected,
        fieldSelected: FieldSelected[V],
        inCondition: V => Boolean
      ) extends ClusterStepBuilder {
        def start(): Option[ID] = withMetric(nbrRange)
        def withMetric(metric: Metric): Option[ID] = disjointCluster[V](
          leaderSelected.leaderCondition,
          fieldSelected.field,
          fieldSelected.policy,
          inCondition,
          metric
        )
      }
    }
  }
}
