package it.scafi.lib.clustering
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

trait ClusteringBuilder {
  self: AggregateProgram
    with StandardSensors
    with BlockG
    with CustomSpawn
    with BlockC
    with ClusteringDefinition
    with ClusteringAbstraction =>

  /**
   * Entry for creating a clustering process
   * @tparam K
   * @tparam I
   * @tparam D
   */
  def cluster[K, I, D]: InputSelection[K, I, D] = InputSelection(ClusterContext())

  abstract private[clustering] class ClusteringFromContext[K, I, D](context: ClusterContext[K, I, D])
      extends Clustering {
    override type Input = I
    override type ClusterData = D
    override type Key = K
    override def input: I = context.input()
    override def localData: D = context.localData()
    override def keyFactory: K = context.keyFactory()
    override def metric: ScafiIncarnationForAlchemist.Metric = context.metric
    override def isCandidate: Boolean = context.candidate()
    override def expand(input: I): I = context.expand(input)
    override def collect(accumulator: D, local: D): D = context.collect(accumulator, local)
    override def finalization(clusterData: D): D = context.finalization(clusterData)
    override def inCondition(key: K, data: I): Boolean = context.inCondition(key, data)
  }

  case class ClusterContext[K, I, D](
    input: () => I = null,
    localData: () => D = null,
    keyFactory: () => K = null,
    candidate: () => Boolean = null,
    expand: I => I = null,
    collect: (D, D) => D = null,
    finalization: D => D = null,
    inCondition: (K, I) => Boolean = null,
    metric: Metric = nbrRange
  ) {}

  case class InputSelection[K, I, D](private val context: ClusterContext[K, I, D]) {
    def input(logic: => I): KeyFactory[K, I, D] = KeyFactory(context.copy(input = () => logic))
  }

  case class KeyFactory[K, I, D](private val context: ClusterContext[K, I, D]) {
    def key(logic: => K): ExpandSelection[K, I, D] = ExpandSelection(context.copy(keyFactory = () => logic))
  }

  case class ExpandSelection[K, I, D](private val context: ClusterContext[K, I, D]) {
    def expand(logic: I => I): LocalDataSelection[K, I, D] = LocalDataSelection(context.copy(expand = i => logic(i)))
    def shareInput: LocalDataSelection[K, I, D] = LocalDataSelection(context.copy(expand = i => i))
  }

  case class LocalDataSelection[K, I, D](private val context: ClusterContext[K, I, D]) {
    def localInformation(logic: => D): CollectSelection[K, I, D] = CollectSelection(
      context.copy(localData = () => logic)
    )
    def withoutDataGathering: CandidateSelection[K, I, Unit] = CandidateSelection(
      context.copy(localData = () => (), collect = (a: Unit, b: Unit) => a, finalization = (a: Unit) => a)
    )
  }

  case class CollectSelection[K, I, D](private val context: ClusterContext[K, I, D]) {
    def collect(logic: (D, D) => D): FinalizationSelection[K, I, D] = FinalizationSelection(
      context.copy(collect = (a: D, b: D) => logic(a, b))
    )
    def collectWithNoFinalization(logic: (D, D) => D): CandidateSelection[K, I, D] = CandidateSelection(
      context.copy(collect = (a: D, b: D) => logic(a, b), finalization = a => a)
    )
  }

  case class FinalizationSelection[K, I, D](private val context: ClusterContext[K, I, D]) {
    def finalize(logic: D => D): CandidateSelection[K, I, D] = CandidateSelection(
      context.copy(finalization = (d: D) => logic(d))
    )
  }

  case class CandidateSelection[K, I, D](private val context: ClusterContext[K, I, D]) {
    def candidate(logic: => Boolean): InConditionSelection[K, I, D] = InConditionSelection(
      context.copy(candidate = () => logic)
    )
  }
  case class InConditionSelection[K, I, D](private val context: ClusterContext[K, I, D]) {
    def inIff(logic: (K, I) => Boolean): DisjointSelection[K, I, D] = DisjointSelection(
      context.copy(inCondition = { case (k, i) => logic(k, i) })
    )
  }

  case class DisjointSelection[K, I, D](private val context: ClusterContext[K, I, D]) {
    def disjoint: Clustering.Aux[K, I, D] with Disjoint = new ClusteringFromContext[K, I, D](context) with Disjoint
    def overlap: Clustering.Aux[K, I, D] = new OverlapClusteringProgram(context, id => id, _ => Set.empty)
    def watchDog(logic: Clustering.ClusteringDivision[K, D] => Set[K]): OverlapFinalizer[K, I, D] = OverlapFinalizer(
      context,
      a => a,
      division => logic(division)
    )
    def merge(logic: Clustering.Cluster[K, D] => Clustering.Cluster[K, D]): WatchDogSelection[K, I, D] =
      WatchDogSelection(context, logic)
  }

  case class WatchDogSelection[K, I, D](
    private val context: ClusterContext[K, I, D],
    private val merge: Clustering.Cluster[K, D] => Clustering.Cluster[K, D]
  ) {
    def watchDog(logic: Clustering.ClusteringDivision[K, D] => Set[K]): OverlapFinalizer[K, I, D] = OverlapFinalizer(
      context,
      merge,
      division => logic(division)
    )
  }

  case class OverlapFinalizer[K, I, D](
    private val context: ClusterContext[K, I, D],
    private val merge: Clustering.Cluster[K, D] => Clustering.Cluster[K, D],
    private val watchDogLogic: Clustering.ClusteringDivision[K, D] => Set[K]
  ) {
    def overlap: Clustering.Aux[K, I, D] = new OverlapClusteringProgram(context, merge, watchDogLogic)
  }

  private class OverlapClusteringProgram[K, I, D](
    context: ClusterContext[K, I, D],
    merge: Clustering.Cluster[K, D] => Clustering.Cluster[K, D],
    watchDogLogic: Clustering.ClusteringDivision[K, D] => Set[K]
  ) extends ClusteringFromContext[K, I, D](context)
      with Overlap {
    override def mergePolicy(divisions: Cluster): Cluster = merge(divisions)

    override def watchDog(division: ClusterDivision): Set[K] = watchDogLogic(division)
  }
}
