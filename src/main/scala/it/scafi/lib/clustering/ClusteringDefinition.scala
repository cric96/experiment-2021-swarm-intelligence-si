package it.scafi.lib.clustering
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

trait ClusteringDefinition {
  self: AggregateProgram with StandardSensors with BlockG with CustomSpawn with BlockC with ClusteringAbstraction =>

  trait ClusteringCommon {
    self: Clustering =>

    def computeSummaryAndShare(leader: Boolean): Option[ClusterData] = {
      val potential = classicGradient(leader, metric)
      val summary =
        C(potential, (left, right) => combineOption(left, right), Some(localData), Option.empty[LocalData])
      val broadcastSummary: Option[ClusterData] =
        broadcast(leader, branch(leader) { summary.map(finalization) } { None })
      broadcastSummary
    }
  }
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

  trait Overlap extends ClusteringCommon {
    self: Clustering =>
    case class OverlapProcessInput[D](inputData: D, toKill: Set[Key])

    def mergePolicy(divisions: Cluster): Cluster
    def watchDog(division: ClusterDivision): Set[Key]

    override def apply(): ClusterDivision = {
      rep((Set.empty[Key], emptyClusterDivision)) { case (toKill, _) =>
        val clusters = findClusters(toKill)
        val unionClusters = clusterUnion(toKill, clusters)
        (watchDog(unionClusters), unionClusters)
      }._2
    }

    private def findClusters(toKill: Set[Key]): Cluster = {
      val processLogic: Key => OverlapProcessInput[Input] => POut[Option[ClusterData]] = { key =>
        { case OverlapProcessInput(input, toKill) =>
          val processOwner = key == keyFactory
          mux(toKill.contains(key)) {
            POut(Option.empty[ClusterData], SpawnInterface.Terminated)
          } {
            val center = mux(processOwner) { Option(input) } { Option.empty[Input] }
            val expandClusterFromLeader = G[Option[Input]](processOwner, center, i => i.map(expand), metric)
            expandClusterFromLeader match {
              case None =>
                POut(Option.empty[ClusterData], SpawnInterface.External)
              case Some(temperature) =>
                branch(inCondition(key, temperature)) {
                  POut(computeSummaryAndShare(processOwner), SpawnInterface.Output)
                } {
                  POut(Option.empty[ClusterData], SpawnInterface.External)
                }
            }
          }
        }
      }
      val clusterKey = mux(isCandidate) { Set(keyFactory) } { Set.empty }
      val processOutput = sspawn2(processLogic, clusterKey, OverlapProcessInput(input, toKill))
      processOutput.collect { case (k, Some(v)) => k -> v }
    }

    private def clusterUnion(toKill: Set[Key], localCluster: Cluster): ClusterDivision = {
      val unionLogic: Key => OverlapProcessInput[Cluster] => POut[ClusterDivision] = { key =>
        { case OverlapProcessInput(input, toKill) =>
          val processOwner = key == keyFactory
          mux(toKill.contains(key)) {
            POut(emptyClusterDivision, SpawnInterface.Terminated)
          } {
            branch(localCluster.keySet.contains(key)) {
              val potential = classicGradient(processOwner, metric)
              val allInformation = C[Double, Cluster](potential, _ ++ _, input, emptyCluster)
              val clusterDivision = ClusterDivision(allInformation, mergePolicy(allInformation))
              val result = broadcast(processOwner, clusterDivision)
              POut(result, SpawnInterface.Output)
            } {
              POut(emptyClusterDivision, SpawnInterface.External)
            }
          }
        }
      }
      val keys = localCluster.keySet
      val clusterMerged = sspawn2(unionLogic, keys, OverlapProcessInput(localCluster, toKill))
      val foldResult = clusterMerged.values
        .foldLeft(emptyClusterDivision) { case (acc, data) =>
          ClusterDivision(acc.all ++ data.all, acc.merged ++ data.merged)
        }
      foldResult
    }
  }
}
