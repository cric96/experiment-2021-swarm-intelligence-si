package it.scafi.lib.clustering
import it.scafi.ProcessFix
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

/**
 * Component that contains the implementation cluster abstraction.
 * Here, I developed the clustering process using standard field calculus and processes:
 * a) Disjoint: tries to implement clustering algorithm without process. This implementation has limitation and
 * it is only a PoF
 * b) Overlap: implementation of clustering algorithm using aggregate processes.
 */
trait ClusteringDefinition {
  self: AggregateProgram with StandardSensors with BlockG with CustomSpawn with BlockC with ClusteringAbstraction =>

  /**
   * Define the common implementation used for both Disjoint and Overlap implementations.
   */
  trait ClusteringCommon {
    self: Clustering =>

    /**
     * Collect phase and Finalization phase (in common for both Disjoint and Overlap definition):
     * The local data will be collect in the leader node using C and the collect function defined.
     * Then, the leader finalize the data collected and share the cluster information to all nodes (using broadcast).
     * So: C (collect) + G (broadcast)
     * @param leader the cluster centre.
     * @return Option.of(ClusterData) if the node is in the cluster of leader, None otherwise
     */
    def computeSummaryAndShare(leader: Boolean): Option[ClusterData] = {
      val potential = classicGradient(leader, metric)
      val summary =
        C(potential, (left, right) => combineOption(left, right), Some(localData), Option.empty[LocalData])
      val broadcastSummary: Option[ClusterData] =
        broadcast(leader, branch(leader) { summary.map(finalization) } { None })
      broadcastSummary
    }
  }

  /**
   * This is the meta-algorithm definitions to find Disjoint cluster.
   * The phases are:
   * a) cluster candidate selection (using *isCandidate*)
   * b) centre data expansion (using *expand*)
   * c) in-out condition check (using *inCondition*)
   * d) local data collection and cluster data computation (using *collect* and *finalize*)
   * e) cluster data sharing.
   * Summarizing, the block used are:
   * G (expansion) + C (collection) + G (share result)
   * 
   * How to create a disjoint algorithm:
   * new Clustering { ... } with Disjoint
   */
  trait Disjoint extends ClusteringCommon {
    self: Clustering =>
    override def apply(): ClusterDivision = {
      // 1. Check if I am the cluster leader
      val clusterCenter = if (isCandidate) { Option(keyFactory) }
      else { Option.empty[Key] }
      // 2. Start to share my local input (e.g. temperature). Expand define how the input change during the expansion
      val expandClusterFromLeader: (Option[Key], Input) =
        G(isCandidate, (clusterCenter, input), (data: (Option[Key], Input)) => (data._1, expand(data._2)), metric)
      // 3. Cluster in-out check
      expandClusterFromLeader match {
        // 3.1 A cluster key is received
        case (Some(key), data) =>
          // 3.1.1 In verification
          branch(inCondition(key, data)) {
            val broadcastSummary = computeSummaryAndShare(key == keyFactory)
            broadcastSummary.map(data => createCluster(key, data)).getOrElse(emptyClusterDivision)
          } {
            // 3.2.1 I'm out, so no cluster is found
            emptyClusterDivision
          }
        // 3.2 No key is received, so no cluster is found
        case _ => emptyClusterDivision
      }
    }
  }

  /**
   * This meta-algorithm allows finding overlapped clusters.
   * It is divided into two parts:
   * a) overlapped cluster generation: the principal part of the algorithm. The cluster formation follows 
   * the disjoint cluster definition.
   * However, here we use Aggregate Processes. Hence, in this case, the "bubbles" could have intersections.
   * b) clusters cleaning: the clusters produced in the phases above should need to be cleaned, in particular:
   *  1. some clusters do not meet the initial condition and should be suppressed (watchdog phase)
   *  2. some clusters have the same shape, therefore we can merge them (union phase)
   *
   * Both union and suppression are performed in the leader node.
   * 
   * The union phase starts another process for each leader.
   * They collect the clusters perceived by the node inside the leader cluster.
   * With that vision, the leader uses *mergePolicy* to choose if its cluster identity should be changed 
   * and leave it to another.
   * Then, share its decision (Key -> ClusterData) to the nodes in its clusters.
   * Summarizing the union phases, the block used are:
   * C(collect clusters) 
   * 
   * How to create an overlap algorithm:
   * new Clustering { ... } with Overlap { ... }
   */
  trait Overlap extends ClusteringCommon {
    self: Clustering =>

    /**
     * Input data for clustering process. It consists in the data needs by the process to compute 
     * the output and a Set that define what process need to be Terminated
     * @param inputData the input data used by the process to compute the output
     * @param toKill the process that will be terminated
     * @tparam D the input data type
     */
    case class OverlapProcessInput[D](inputData: D, toKill: Set[Key])

    /**
     * Define if the current cluster (*reference*, identified by the key) loses its identity and became one of the
     * other cluster in zoneClusters.
     * @param reference the key of the leader that verifies if it looses its identity or not
     * @param zoneClusters the cluster founded in the zone where the node is leader
     * @return (reference -> zoneClusters(reference)) if the leader mantains its identity, anther key, clusterData otherwise
     */
    def mergePolicy(reference: Key, zoneClusters: Cluster): (Key, ClusterData)

    /**
     * The policy used to kill clustering process. 
     * @return The key of processes that need to be Terminated by the leader.
     */
    def watchDog(): Set[Key]

    override def apply(): ClusterDivision = {
      val toKill = watchDog() // check what processes need to be killed
      val clusters = findClusters(toKill) // phase a) overlapped clusters generation
      clusterUnion(toKill, clusters) // phase b) cluster union
    }

    private def findClusters(toKill: Set[Key]): Cluster = {
      val processLogic: Key => OverlapProcessInput[Input] => POut[Option[ClusterData]] = { key =>
        { case OverlapProcessInput(input, toKill) =>
          killProcessOrCompute(toKill, key) { // helper to suppress clustering process
            val processOwner = key == keyFactory
            // 1. Check if the node is the leader and prepare the input to share to other nodes
            val center = mux(processOwner) { Option(input) } { Option.empty[Input] }
            // 2. Expand the input generated from the leader
            val expandClusterFromLeader = G[Option[Input]](processOwner, center, i => i.map(expand), metric)
            // 3. Compute the cluster data and share to other nodes
            expandClusterFromLeader match {
              // 3.1 I am outside of current process, so I return External as result
              case None =>
                POut(Option.empty[ClusterData], SpawnInterface.External)
              // 3.2 I have received the data from the current process owner
              case Some(leaderData) =>
                // 3.2.1 I verify if I am inside or outside this cluster
                branch(inCondition(key, leaderData)) {
                  // 3.2.2 If I am inside, I partecipate into the local data collection and finalization (so return Output)
                  POut(computeSummaryAndShare(processOwner), SpawnInterface.Output)
                } {
                  // 3.2.3 Otherwise I am outside of the current cluster, so I return external
                  POut(Option.empty[ClusterData], SpawnInterface.External)
                }
            }
          }
        }
      }
      // 0. new processes selection using *isCandidate* condition
      val clusterKey = mux(isCandidate) { Set(keyFactory) } { Set.empty }
      // Clusters evaluation
      val processOutput = sspawn2(processLogic, clusterKey, OverlapProcessInput(input, toKill))
      processOutput.collect { case (k, Some(v)) => k -> v }
    }

    private def clusterUnion(toKill: Set[Key], localCluster: Cluster): ClusterDivision = {
      val unionLogic: Key => OverlapProcessInput[Cluster] => POut[Option[(Key, ClusterData)]] = { key =>
        { case OverlapProcessInput(input, toKill) =>
          killProcessOrCompute(toKill, key) { // helper to suppress clustering process
            val processOwner = key == keyFactory
            branch(localCluster.keySet.contains(key)) { // Expand process in node that contains my key as a cluster
              val potential = classicGradient(processOwner, metric)
              // 1. collect all cluster computed by nodes
              val allInformation = C[Double, Cluster](potential, _ ++ _, input, emptyCluster)
              // 2. merge the clusters (leader perform the operation)
              val shareDecision = mux(processOwner) { mergePolicy(key, allInformation) } {
                // identity. safe because I am in branch where localCluster contains key
                key -> localCluster(key)
              }
              // 3. share the leader decision to all other nodes
              POut(Option(broadcast(processOwner, shareDecision)), SpawnInterface.Output)
            } {
              POut(Option.empty[(Key, ClusterData)], SpawnInterface.External)
            }
          }
        }
      }
      val keys = localCluster.keySet // new processes following the cluster output
      val mergeProcessResult = sspawn2(unionLogic, keys, OverlapProcessInput(localCluster, toKill))
      // point-wise merge operation following leader decision
      val clusterMerged = mergeProcessResult.values.collect { case Some((key, data)) => (key, data) }.toMap
      ClusterDivision(localCluster, clusterMerged)
    }

    /*
     * helper used to define a body process where it termination depend of a key set.
     */
    private def killProcessOrCompute[O](toKill: Set[Key], processKey: Key)(
      processLogic: => POut[Option[O]]
    ): POut[Option[O]] = {
      mux(toKill.contains(processKey)) {
        POut(Option.empty[O], SpawnInterface.Terminated)
      } {
        processLogic
      }
    }
  }

}
