package it.casestudy

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
trait ClusteringLib {
  self: AggregateProgram with StandardSensors with BlockG with CustomSpawn =>
  // TODO Add documentation
  trait ClusteringProcess[K, I, O] extends (() => Map[K, O]) {
    def process(k: K, i: I): O
    def input: I
    def inCondition(k: K, i: I, o: O): Boolean
    def keyFactory: K
    def candidate: Boolean
  }

  trait Disjoint[K, I, O] {
    self: ClusteringProcess[K, I, O] =>
    def metric: Metric
    final override def apply(): Map[K, O] = {
      val clusterValue = if (candidate) { Option(keyFactory) }
      else { Option.empty[K] }
      val expand = G[Option[K]](candidate, clusterValue, a => a, metric)
      val cluster = expand.map(k => k -> process(k, input))
      cluster.toMap
    }
  }

  trait Overlap[K, I, O] {
    self: ClusteringProcess[K, I, O] =>
    def watchDog(cluster: Map[K, O], input: I): Set[K]
    def merge(map: Map[K, O]): Map[K, O]
    final override def apply(): Map[K, O] = {
      rep(Map.empty[K, O]) { clusters =>
        val toKill = watchDog(clusters, input)
        val key = mux(candidate) { Set(keyFactory) } { Set.empty }
        val processes = sspawn2[K, (I, Set[K]), Option[O]](spawnLogic, key, (input, toKill))
        val withResult = processes.collect { case (k, Some(v)) => k -> v }
        merge(withResult)
      }
    }
    private val spawnLogic: K => ((I, Set[K])) => POut[Option[O]] = { k =>
      { case (input, toKill) =>
        mux(toKill.contains(k)) {
          POut(Option.empty[O], SpawnInterface.Terminated)
        } {
          val output = process(k, input)
          branch(inCondition(k, input, output)) {
            POut(Option(process(k, input)), SpawnInterface.Output)
          } {
            POut(Option.empty[O], SpawnInterface.External)
          }
        }
      }
    }
  }

  def cluster[K, I, O]: InputSelection[K, I, O] = InputSelection[K, I, O]()

  abstract private class ClusterProcessFromContext[K, I, O](context: CommonClusterContext[K, I, O])
      extends ClusteringProcess[K, I, O] {
    override def process(k: K, i: I): O = context.process(k)(i)

    override def input: I = context.input()

    override def inCondition(k: K, i: I, o: O): Boolean = context.inCondition(k)(i)(o)

    override def keyFactory: K = context.keyFactory()

    override def candidate: Boolean = context.candidate()
  }

  case class CommonClusterContext[K, I, O](
    input: () => I = null,
    keyFactory: () => K = null,
    inCondition: K => I => O => Boolean = null,
    candidate: () => Boolean = null,
    process: K => I => O = null
  ) {}

  case class InputSelection[K, I, O](
    private val context: CommonClusterContext[K, I, O] = CommonClusterContext[K, I, O]()
  ) {
    def input(logic: => I): KeyGeneratorSelection[K, I, O] = KeyGeneratorSelection(context.copy(input = () => logic))
  }

  case class KeyGeneratorSelection[K, I, O](private val context: CommonClusterContext[K, I, O]) {
    def keyGenerator(logic: => K): ProcessSelection[K, I, O] = ProcessSelection(
      context.copy(keyFactory = () => logic)
    )
  }

  case class ProcessSelection[K, I, O](private val context: CommonClusterContext[K, I, O]) {
    def process(logic: K => I => O): InCondition[K, I, O] = InCondition[K, I, O](context.copy(process = logic))
  }

  case class InCondition[K, I, O](private val context: CommonClusterContext[K, I, O]) {
    def insideIf(logic: K => I => O => Boolean): CandidateSelection[K, I, O] = CandidateSelection(
      context.copy(inCondition = logic)
    )
  }

  case class CandidateSelection[K, I, O](private val context: CommonClusterContext[K, I, O]) {
    def candidate(logic: => Boolean): FinalizerCommon[K, I, O] = FinalizerCommon(
      context.copy(candidate = () => logic)
    )
  }

  case class FinalizerCommon[K, I, O](private val context: CommonClusterContext[K, I, O])
      extends OverlapFinalizer[K, I, O](
        (clusters: Map[K, O]) => clusters,
        (_: Map[K, O]) => Set.empty,
        context = context
      ) {
    def disjoint: () => Map[K, O] = new ClusterProcessFromContext(context) with Disjoint[K, I, O] {
      override def metric: ScafiIncarnationForAlchemist.Metric = nbrRange
    }
    def mergeWhen(mergePolicy: Map[K, O] => Map[K, O]): MergeSelected[K, I, O] =
      MergeSelected(context, mergePolicy)
  }

  case class MergeSelected[K, I, O](
    private val context: CommonClusterContext[K, I, O],
    private val mergePolicy: Map[K, O] => Map[K, O]
  ) extends OverlapFinalizer[K, I, O](
        mergePolicy,
        (clusters: Map[K, O]) => Set.empty,
        context = context
      ) {
    def killWhen(killPolicy: Map[K, O] => Set[K]): OverlapFinalizer[K, I, O] =
      new OverlapFinalizer(mergePolicy, killPolicy, context)
  }

  class OverlapFinalizer[K, I, O](
    private val mergePolicy: Map[K, O] => Map[K, O],
    private val killPolicy: Map[K, O] => Set[K],
    private val context: CommonClusterContext[K, I, O]
  ) {
    def overlap: () => Map[K, O] = new ClusterProcessFromContext(context) with Overlap[K, I, O] {
      override def watchDog(cluster: Map[K, O], input: I): Set[K] = killPolicy(cluster)
      override def merge(map: Map[K, O]): Map[K, O] = mergePolicy(map)
    }
  }
}
