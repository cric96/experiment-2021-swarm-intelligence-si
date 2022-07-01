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
   * Entry for creating a clustering process.
   * example:
   * cluster
   *  .input(0) // distance from leader, 0 if I am the leader
   *  .key(mid) // node id as key
   *  .expand(i => i + nbrRange()) // compute the distance from leader.
   *  .withoutDataGathering // no data gathering, I do not compute a cluster data. I am interested only in the division (partitioning)
   *  .candidate(mid() % 10 == 0) // start process in node that has an id multiple of ten
   *  .inIff((key, distance) => distance < 30)) // I am in if the distance from the leader is lesser then 30 units
   *  .overlap() //overlap means that multiple process will be spawned,
   *
   * for more building example, see it.examples.ClusterExamples
   * @tparam K the cluster key type (e.g. Int? ID?)
   * @tparam I the input cluster expansion data (e.g. temperature? cluster range?)
   * @tparam D local data used to compute cluster information
   * @tparam C cluster data used to describe a cluster found
   */
  def cluster[K, I, D, C]: InputSelection[K, I, D, C] = InputSelection(ClusterContext())

  /* Base class used from both create Disjoint and Overlap instance. Its behaviour is based on a context that
   * contains all the information needed to instantiate a clustering algorithm */
  abstract private[clustering] class ClusteringFromContext[K, I, D, C](private val context: ClusterContext[K, I, D, C])
      extends Clustering {
    override type Input = I
    override type ClusterData = C
    override type LocalData = D
    override type Key = K

    override def input: I = context.input()
    override def localData: D = context.localData()
    override def keyFactory: K = context.keyFactory()
    override def metric: ScafiIncarnationForAlchemist.Metric = context.metric
    override def isCandidate: Boolean = context.candidate()
    override def expand(input: I): I = context.expand(input)
    override def collect(accumulator: D, local: D): D = context.collect(accumulator, local)
    override def finalization(clusterData: D): C = context.finalization(clusterData)
    override def inCondition(key: K, data: I): Boolean = context.inCondition(key, data)
    override def lastWillCount: Int = context.lastWillCounts()
  }
  /* helper to build a clustering algorithm. It contains all the function needed. Outside of clustering, this should be
   * not visible, because expose null initialization. The builder step below allows a safe creation.s */
  private[clustering] case class ClusterContext[K, I, D, C](
    input: () => I = null,
    localData: () => D = null,
    keyFactory: () => K = null,
    candidate: () => Boolean = null,
    expand: I => I = null,
    collect: (D, D) => D = null,
    finalization: D => C = null,
    inCondition: (K, I) => Boolean = null,
    metric: Metric = nbrRange,
    lastWillCounts: () => Int = null
  ) {}

  /*
  ******** STEP BUILDER PATTERN ********
   * Phases:
   * input selection
   *   |
   *   v
   * key selection
   *   |
   *   v
   * expand policy definition ---------------
   *   |                                    |
   *   V                                    |
   * local data                             |
   *   |                                    |
   *   V                                    | *no local data collection*
   * collect policy definition --------     |
   *   |           *no finalization*   |    |
   *   V                               |    |
   * finalization strategy definition  |    |
   *   |                               |    |
   *   V                               |    |
   * candidate selection  <------------------
   *   |
   *   V
   * in condition selection ---------> disjoint
   *   |
   *   V
   * merge selection
   *   |
   *   V
   * watch dog selection ---------> disjoint
   *
   */
  // Input selection phases: user defines what is the input hat will guide the cluster expansion phase (e.g. cluster.input(0))
  case class InputSelection[K, I, D, C](private val context: ClusterContext[K, I, D, C]) {
    def input(logic: => I): KeyFactorySelection[K, I, D, C] = KeyFactorySelection(context.copy(input = () => logic))
  }
  // Key factory selection: user defines what is the key generation policy (e.g. builder.key { mid() })
  case class KeyFactorySelection[K, I, D, C](private val context: ClusterContext[K, I, D, C]) {
    def key(logic: => K): ExpandSelection[K, I, D, C] = ExpandSelection(context.copy(keyFactory = () => logic))
  }
  // Expand policy selection: user defines how the input change during the expansion from leader to other nodes
  case class ExpandSelection[K, I, D, C](private val context: ClusterContext[K, I, D, C]) {

    /** evolve the leader data in gradient cast using the logic passed */
    def expand(logic: I => I): LocalDataSelection[K, I, D, C] = LocalDataSelection(context.copy(expand = i => logic(i)))

    /** broadcast the leader data */
    def shareInput: LocalDataSelection[K, I, D, C] = LocalDataSelection(context.copy(expand = i => i))
  }
  // Local data selection: user defines what is the local data useful for compute cluster data information (e.g. builder.localInformation { currentPosition })
  case class LocalDataSelection[K, I, D, C](private val context: ClusterContext[K, I, D, C]) {
    def localInformation(logic: => D): CollectSelection[K, I, D, C] = CollectSelection(
      context.copy(localData = () => logic)
    )

    /** jump directly to candidate selection. In this process we are not interested in cluster data but only in the partitions */
    def withoutDataGathering: CandidateSelection[K, I, Unit, Unit] = CandidateSelection(
      context.copy(localData = () => (), collect = (a: Unit, b: Unit) => a, finalization = (a: Unit) => a)
    )
  }
  // Collect policy selection: user define how the local data will be combined during the C collection to the leader
  case class CollectSelection[K, I, D, C](private val context: ClusterContext[K, I, D, C]) {
    def collect(logic: (D, D) => D): FinalizationSelection[K, I, D, C] = FinalizationSelection(
      context.copy(collect = (a: D, b: D) => logic(a, b))
    )

    /** user does not care about the finalization phase, so this jump directly to candidate selection (so D =:= C) */
    def collectWithNoFinalization(logic: (D, D) => D): CandidateSelection[K, I, D, D] = CandidateSelection(
      context.copy(collect = (a: D, b: D) => logic(a, b), finalization = a => a)
    )
  }
  // Finalization policy selection: user defines how to change the local data collected to the cluster data
  case class FinalizationSelection[K, I, D, C](private val context: ClusterContext[K, I, D, C]) {
    def finalize(logic: D => C): CandidateSelection[K, I, D, C] = CandidateSelection(
      context.copy(finalization = (d: D) => logic(d))
    )
  }
  // Candidate selection: user defines when a node became a candidate (e.g. builder.candidate { temperature < 10.0 })
  case class CandidateSelection[K, I, D, C](private val context: ClusterContext[K, I, D, C]) {
    def candidate(logic: => Boolean): InConditionSelection[K, I, D, C] = InConditionSelection(
      context.copy(candidate = () => logic)
    )
  }
  // In condition selection: user defines if a node is inside a cluster or not. (e.g. builder.inIff { (key, range) => range < 30.0 }
  case class InConditionSelection[K, I, D, C](private val context: ClusterContext[K, I, D, C]) {
    def inIff(logic: (K, I) => Boolean): DisjointSelection[K, I, D, C] = DisjointSelection(
      context.copy(inCondition = { case (k, i) => logic(k, i) })
    )
  }
  /*
    Disjoint selection: user could choices to build a disjoint clustering algorithm (using disjoint)
    or build an overlap clustering with no merge/watchdog policy (using overlap)
    or continuing the building phase defining the watchdog policy (using watchDog)
    or continuing the building phase defining the merge policy (using merge)
   */
  case class DisjointSelection[K, I, D, C](private val context: ClusterContext[K, I, D, C]) {
    def disjoint: Clustering.Aux[K, I, D, C] with Disjoint = new ClusteringFromContext[K, I, D, C](context)
      with Disjoint
    def overlap: Clustering.Aux[K, I, D, C] =
      new OverlapClusteringProgram(context, (k, cluster) => k -> cluster(k), () => Set.empty)
    def watchDog(logic: => Set[K]): OverlapFinalizer[K, I, D, C] = OverlapFinalizer(
      context,
      (k, cluster) => k -> cluster(k),
      () => logic
    )
    def merge(logic: (K, Clustering.Cluster[K, C]) => (K, C)): WatchDogSelection[K, I, D, C] =
      WatchDogSelection(context, logic)
  }
  // Watchdog selection: user define how to kill process
  case class WatchDogSelection[K, I, D, C](
    private val context: ClusterContext[K, I, D, C],
    private val merge: (K, Clustering.Cluster[K, C]) => (K, C)
  ) {
    def watchDog(logic: => Set[K]): OverlapFinalizer[K, I, D, C] = OverlapFinalizer(
      context,
      merge,
      () => logic
    )
  }
  // utility to build overlap clustering process (e.g. builder.overlap())
  case class OverlapFinalizer[K, I, D, C](
    private val context: ClusterContext[K, I, D, C],
    private val merge: (K, Clustering.Cluster[K, C]) => (K, C),
    private val watchDogLogic: () => Set[K]
  ) {
    def overlap: Clustering.Aux[K, I, D, C] = new OverlapClusteringProgram(context, merge, watchDogLogic)
  }
  // utility to build clustering algorithm with possible overlapping
  private class OverlapClusteringProgram[K, I, D, C](
    context: ClusterContext[K, I, D, C],
    merge: (K, Clustering.Cluster[K, C]) => (K, C),
    watchDogLogic: () => Set[K]
  ) extends ClusteringFromContext[K, I, D, C](context)
      with Overlap {

    override def watchDog(): Set[K] = watchDogLogic()

    override def mergePolicy(reference: K, zoneClusters: Cluster): (K, C) = merge(reference, zoneClusters)
  }
}
