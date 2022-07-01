package it.examples

import it.scafi.lib.BlocksWithShare
import it.scafi.lib.clustering.ClusteringLib
import it.scafi.{ProcessFix, SenseLayers}
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist.{ScafiAlchemistSupport, _}

/* This file contains two example of Clustering API usage.
 *   a) clustering based on a perceived value (temperature)
 *   b) clustering based on the node number
 * to run these examples type:
 *
 * ./gradlew runTestDisjointClusterGraphic
 * ./gradlew runTestOverlapClusterGraphic
 * ./gradlew runTestClusterOnNumberGraphic
 *
 * */

trait Libs
    extends AggregateProgram
    with SenseLayers
    with StandardSensors
    with ScafiAlchemistSupport
    with FieldUtils
    with Gradients
    with BlockG
    with BlockC
    with ProcessFix
    with TimeUtils
    with StateManagement
    with BlocksWithShare
    with ClusteringLib {}

class TemperatureDisjointedBased extends Libs {
  def temperatureToBroadcast(id: ID, temperature: Double): Double = mux(id == mid()) { temperature } { -1 }
  override def main(): Any = {
    val temperature: Double = sense[java.lang.Double]("temperature")
    val thr = 0.5
    val id = includingSelf.minHoodSelector(nbr(temperature))(nbr(mid()))
    val clusterStarter = branch(id == mid()) { T(5) <= 0 } { false }
    val clusters = cluster
      .input(temperature)
      .key(mid())
      .shareInput
      .withoutDataGathering
      .candidate(clusterStarter)
      .inIff((_, leaderTemp) => Math.abs(leaderTemp - temperature) <= thr)
      .disjoint()
    node.put("candidate", clusterStarter)
    node.put("clusters", clusters.merged.keySet)
  }
}

class TemperatureOverlapBased extends Libs {
  def temperatureToBroadcast(id: ID, temperature: Double): Double = mux(id == mid()) { temperature } { -1 }
  def noMoreCandidate(candidate: Boolean): Set[ID] = mux(!candidate) { Set(mid()) } { Set.empty }

  override def main(): Any = {
    val temperature: Double = sense[java.lang.Double]("temperature")
    val thr = 0.5
    val id = includingSelf.minHoodSelector(nbr(temperature))(nbr(mid()))
    val neighCount = foldhoodPlus(0)(_ + _)(1)
    node.put("neigh", excludingSelf.reifyField(1))
    node.put("count", neighCount)
    val clusterStarter = branch(id == mid()) { T(5) <= 0 && neighCount > 1 } { false }
    val clusters = rep(emptyClusterDivision[ID, Unit]) { feedback =>
      cluster
        .input(temperature)
        .key(mid())
        .shareInput
        .withoutDataGathering
        .candidate(clusterStarter)
        .inIff((_, leaderTemp) => Math.abs(leaderTemp - temperature) <= thr)
        .watchDog {
          noMoreCandidate(clusterStarter) ++ lastWillWatchDog(feedback.all, 5, identity[ID])
        }
        .overlap()
    }
    node.put("candidate", clusterStarter)
    node.put("clusters", clusters.merged.keySet)
  }
}

class ClusterBasedOnNumber extends Libs {
  def computeRange(division: Clustering.Cluster[ID, Int], initialRange: Double, target: Int, delta: Double): Double =
    rep(initialRange) { range =>
      val merged = division
      branch(merged.contains(mid())) {
        if (merged(mid()) == target) { range }
        else if (merged(mid()) > target) { range - delta }
        else { range + delta }
      } {
        initialRange
      }
    }
  override def main(): Any = {
    val temperature: Double = sense[java.lang.Double]("temperature")
    val id = includingSelf.minHoodSelector(nbr(temperature))(nbr(mid()))
    val candidate = branch(id == mid()) { T(5) <= 0 } { false }
    val initialRange = 0.0
    val delta = 0.01
    val howMany = 10
    val clusters = rep(emptyClusterDivision[ID, Int]) { division =>
      {
        cluster
          .input { computeRange(division.merged, initialRange, howMany, delta) }
          .key(mid())
          .expand(r => r - nbrRange())
          .localInformation(1)
          .collectWithNoFinalization(_ + _)
          .candidate(candidate)
          .inIff { (_, range) => range >= 0 }
          .overlap()
      }
    }
    node.put("candidate", candidate)
    node.put("clusters", clusters.merged.keySet)
  }
}
