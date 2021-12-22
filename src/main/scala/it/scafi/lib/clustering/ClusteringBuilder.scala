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

  abstract private class ClusteringFromContext[K, I, D](context: ClusterContext[K, I, D]) extends Clustering {
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

  case class InputSelection[K, I, D](private val context: ClusterContext[K, I, D]) {
    def input(logic: => I): LocalDataSelection[K, I, D] = LocalDataSelection(context.copy(input = () => logic))
  }

  case class LocalDataSelection[K, I, D](private val context: ClusterContext[K, I, D]) {
    def localInformation(logic: => D): KeyFactory[K, I, D] = KeyFactory(context.copy(localData = () => logic))
  }

  case class KeyFactory[K, I, D](private val context: ClusterContext[K, I, D]) {
    def key(logic: => K): ExpandSelection[K, I, D] = ExpandSelection(context.copy(keyFactory = () => logic))
  }

  case class ExpandSelection[K, I, D](private val context: ClusterContext[K, I, D]) {
    def expand(logic: I => I): CollectSelection[K, I, D] = CollectSelection(context.copy(expand = i => logic(i)))
  }

  case class CollectSelection[K, I, D](private val context: ClusterContext[K, I, D]) {
    def collect(logic: (D, D) => D): FinalizationSelection[K, I, D] = FinalizationSelection(
      context.copy(collect = (a: D, b: D) => logic(a, b))
    )
  }

  case class FinalizationSelection[K, I, D](private val context: ClusterContext[K, I, D]) {
    def finalize(logic: D => D): InConditionSelection[K, I, D] = InConditionSelection(
      context.copy(finalization = d => logic(d))
    )
  }

  case class InConditionSelection[K, I, D](private val context: ClusterContext[K, I, D]) {
    def inIff(logic: (K, I) => Boolean): DisjointSelection[K, I, D] = DisjointSelection(
      context.copy(inCondition = { case (k, i) => logic(k, i) })
    )
  }

  case class DisjointSelection[K, I, D](private val context: ClusterContext[K, I, D]) {
    def disjoint: Clustering.Aux[K, I, D] with Disjoint = new ClusteringFromContext[K, I, D](context) with Disjoint
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
    def overlap: Clustering.Aux[K, I, D] = new ClusteringFromContext[K, I, D](context) with Overlap {
      override def mergePolicy(divisions: Cluster): Cluster = merge(divisions)

      override def watchDog(division: ClusterDivision): Set[Key] = watchDogLogic(division)
    }
  }
}
