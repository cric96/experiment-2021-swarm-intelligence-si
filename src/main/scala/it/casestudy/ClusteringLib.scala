package it.casestudy

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
// TODO move the implementations details here and expose a clear API for clusters formation
trait ClusteringLib {
  self: AggregateProgram with StandardSensors with BlockG =>

  /**
   * Creates disjoint clusters identified by the ID of the leader node
   * @param clusterCandidate a Boolean field that defines if a node is a cluster candidate (i.e. the center of the cluster) or nnot
   * @param clusterField the data necessary to build the cluster (i.e. a temperature field, the potential, ...)
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
   * Facades for cluster identification
   */
  object cluster {

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
