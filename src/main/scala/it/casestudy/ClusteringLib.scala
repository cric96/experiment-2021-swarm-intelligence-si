package it.casestudy

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
trait ClusteringLib {
  self: AggregateProgram with StandardSensors with BlockG with CustomSpawn with ScafiAlchemistSupport =>
  type Cluster[K, O] = Map[K, O]
  def emptyCluster[K, O] = Map.empty[K, O]
  def createCluster[K, O](clusterKey: K, clusterData: O): Cluster[K, O] = Map(clusterKey -> clusterData)

  /**
   * 
   * @tparam K clustering key (i.e. the identifier)
   * @tparam I the input for the clustering process
   * @tparam O the output produced for each cluster
   */
  trait ClusteringProcess[K, I, O] extends (() => Cluster[K, O]) {
    def process(k: K, i: I): O
    def input: I
    def inCondition(k: K, i: I, o: O): Boolean
    def keyFactory: K
    def candidate(cluster: Cluster[K, O]): Boolean
  }

  trait Disjoint[K, I, O] {
    self: ClusteringProcess[K, I, O] =>
    def metric: Metric
    final override def apply(): Cluster[K, O] = {
      rep(emptyCluster[K, O]) { oldCluster =>
        val clusterValue = if (candidate(oldCluster)) { Option(keyFactory) }
        else { Option.empty[K] }
        val expand = G[Option[K]](candidate(oldCluster), clusterValue, a => a, metric)
        val cluster = expand.map(clusterKey => clusterKey -> process(clusterKey, input))
        cluster match {
          case Some((clusterKey, clusterOutput)) if inCondition(clusterKey, input, clusterOutput) =>
            createCluster(clusterKey, clusterOutput)
          case _ => emptyCluster
        }
      }
    }
  }

  trait Overlap[K, I, O] {
    self: ClusteringProcess[K, I, O] =>
    def watchDog(cluster: Cluster[K, O], input: I): Set[K]
    def merge(map: Cluster[K, O]): Cluster[K, O]
    final override def apply(): Cluster[K, O] = {
      rep(emptyCluster[K, O]) { clusters =>
        node.put("clustersKeys", clusters.keySet)
        val toKill = watchDog(clusters, input)
        val clusterKey = mux(candidate(clusters)) { Set(keyFactory) } { Set.empty }
        val processes = sspawn2[K, (I, Set[K]), Option[O]](spawnLogic, clusterKey, (input, toKill))
        node.put("nonMerged", processes.keySet)
        val withResult = processes.collect { case (k, Some(v)) => k -> v }
        merge(withResult)
      }
    }
    private val spawnLogic: K => ((I, Set[K])) => POut[Option[O]] = { clusterKey =>
      { case (input, toKill) =>
        mux(toKill.contains(clusterKey)) {
          POut(Option.empty[O], SpawnInterface.Terminated)
        } {
          val output = process(clusterKey, input)
          mux(inCondition(clusterKey, input, output)) {
            POut(Option(process(clusterKey, input)), SpawnInterface.Output)
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

    override def candidate(cluster: Cluster[K, O]): Boolean = context.candidate(cluster)
  }

  case class CommonClusterContext[K, I, O](
    input: () => I = null,
    keyFactory: () => K = null,
    inCondition: K => I => O => Boolean = null,
    candidate: (Cluster[K, O]) => Boolean = null,
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
    def candidate(logic: => Boolean): FinalizerCommon[K, I, O] = candidateWithFeedback(_ => logic)
    def candidateWithFeedback(logic: Cluster[K, O] => Boolean): FinalizerCommon[K, I, O] = FinalizerCommon(
      context.copy(candidate = logic)
    )
  }

  case class FinalizerCommon[K, I, O](private val context: CommonClusterContext[K, I, O])
      extends OverlapFinalizer[K, I, O](
        (clusters: Cluster[K, O]) => clusters,
        (_: Cluster[K, O]) => Set.empty,
        context = context
      ) {
    def disjoint: () => Cluster[K, O] = new ClusterProcessFromContext(context) with Disjoint[K, I, O] {
      override def metric: ScafiIncarnationForAlchemist.Metric = nbrRange
    }
    def mergeWhen(mergePolicy: Cluster[K, O] => Cluster[K, O]): MergeSelected[K, I, O] =
      MergeSelected(context, mergePolicy)
  }

  case class MergeSelected[K, I, O](
    private val context: CommonClusterContext[K, I, O],
    private val mergePolicy: Cluster[K, O] => Cluster[K, O]
  ) extends OverlapFinalizer[K, I, O](
        mergePolicy,
        (_: Cluster[K, O]) => Set.empty,
        context = context
      ) {
    def killWhen(killPolicy: Cluster[K, O] => Set[K]): OverlapFinalizer[K, I, O] =
      new OverlapFinalizer(mergePolicy, killPolicy, context)
  }

  class OverlapFinalizer[K, I, O](
    private val mergePolicy: Cluster[K, O] => Cluster[K, O],
    private val killPolicy: Cluster[K, O] => Set[K],
    private val context: CommonClusterContext[K, I, O]
  ) {
    def overlap: () => Cluster[K, O] = new ClusterProcessFromContext(context) with Overlap[K, I, O] {
      override def watchDog(cluster: Cluster[K, O], input: I): Set[K] = killPolicy(cluster)
      override def merge(map: Cluster[K, O]): Cluster[K, O] = mergePolicy(map)
    }
  }
}
