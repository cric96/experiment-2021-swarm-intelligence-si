package it.casestudy

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

trait ClusteringLib {
  self: AggregateProgram with StandardSensors with BlockG with CustomSpawn with BlockC with ScafiAlchemistSupport =>
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
    def isCandidate(cluster: Cluster[K, O]): Boolean
  }

  trait Disjoint[K, I, O] {
    self: ClusteringProcess[K, I, O] =>
    def metric: Metric
    final override def apply(): Cluster[K, O] = {
      rep(emptyCluster[K, O]) { oldCluster =>
        val clusterValue = if (isCandidate(oldCluster)) { Option(keyFactory) }
        else { Option.empty[K] }
        val expand = G[Option[K]](isCandidate(oldCluster), clusterValue, a => a, metric)
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
    case class OverlapProcessInput[PI](input: PI, toKill: Set[K])
    def watchDog(cluster: Cluster[K, O], input: I): Set[K]
    def merge(map: Cluster[K, O]): Cluster[K, O]
    final override def apply(): Cluster[K, O] = {
      rep(emptyCluster[K, O]) { clusters =>
        val toKill = watchDog(clusters, input)
        val clusterKey = mux(isCandidate(clusters)) { Set(keyFactory) } { Set.empty }
        val processes =
          sspawn2[K, OverlapProcessInput[I], Option[O]](spawnLogic, clusterKey, OverlapProcessInput(input, toKill))
        val clustersFound = processes.collect { case (k, Some(v)) => k -> v }
        node.put("allClusters", clustersFound.keySet)
        /*val merged = // todo move out of there
          sspawn2[K, OverlapProcessInput[Cluster[K, O]], Cluster[K, O]](
            mergeLogic,
            clustersFound.keySet,
            OverlapProcessInput(clustersFound, toKill)
          )
        node.put("mergedIncredible", merged)
        merged.flatMap { case (_, v) => v }*/
        merge(clustersFound)
      }
    }
    private val spawnLogic: K => OverlapProcessInput[I] => POut[Option[O]] = { clusterKey =>
      { case OverlapProcessInput(input, toKill) =>
        mux(toKill.contains(clusterKey)) {
          POut(Option.empty[O], SpawnInterface.Terminated)
        } {
          val output = process(clusterKey, input)
          mux(inCondition(clusterKey, input, output)) {
            POut(Option(output), SpawnInterface.Output)
          } {
            POut(Option.empty[O], SpawnInterface.External)
          }
        }
      }
    }

    private val mergeLogic: K => OverlapProcessInput[Cluster[K, O]] => POut[Cluster[K, O]] = { clusterKey =>
      { case OverlapProcessInput(clusters, toKill) =>
        mux(toKill.contains(keyFactory)) {
          POut(Map.empty[K, O], SpawnInterface.Terminated)
        } {
          mux(!clusters.keySet.contains(clusterKey)) {
            POut(Map.empty[K, O], SpawnInterface.External)
          } {
            val leader = keyFactory == clusterKey
            val potential = classicGradient(leader)
            val collected = C[Double, Cluster[K, O]](potential, _ ++ _, clusters, Map.empty)
            val merged: Cluster[K, O] = merge(collected)
            POut(broadcast(leader, merged), SpawnInterface.Output)
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

    override def isCandidate(cluster: Cluster[K, O]): Boolean = context.candidateCondition(cluster)
  }

  case class CommonClusterContext[K, I, O](
    input: () => I = null,
    keyFactory: () => K = null,
    inCondition: K => I => O => Boolean = null,
    candidateCondition: (Cluster[K, O]) => Boolean = null,
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
    def candidateCondition(logic: => Boolean): FinalizerCommon[K, I, O] = candidateWithFeedback(_ => logic)
    def candidateWithFeedback(logic: Cluster[K, O] => Boolean): FinalizerCommon[K, I, O] = FinalizerCommon(
      context.copy(candidateCondition = logic)
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
