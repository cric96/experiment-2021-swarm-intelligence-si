package it.examples

import it.casestudy.ClusteringLib
import it.scafi.SenseLayers
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist.{ScafiAlchemistSupport, _}

trait Libs
    extends AggregateProgram
    with SenseLayers
    with StandardSensors
    with ScafiAlchemistSupport
    with FieldUtils
    with Gradients
    with BlockG
    with BlockC
    with CustomSpawn
    with TimeUtils
    with StateManagement
    with ClusteringLib {}

class TemperatureDisjointedBased extends Libs {
  def temperatureToBroadcast(id: ID, temperature: Double): Double = mux(id == mid()) { temperature } { -1 }
  override def main(): Any = {
    val temperature: Double = sense[java.lang.Double]("temperature")
    val thr = 0.5
    val id = includingSelf.minHoodSelector(nbr(temperature))(nbr(mid()))
    val candidate = id == mid()
    val clusters = cluster
      .input(temperature)
      .keyGenerator(mid())
      .process(id => input => broadcast(mid() == id, temperatureToBroadcast(id, input)))
      .insideIf(_ => myTemp => leaderTemp => Math.abs(myTemp - leaderTemp) <= thr)
      .candidateCondition { candidate }
      .disjoint()
    node.put("candidate", candidate)
    node.put("clusters", clusters.keySet)
  }
}

class TemperatureOverlapBased extends Libs {
  def temperatureToBroadcast(id: ID, temperature: Double): Double = mux(id == mid()) { temperature } { -1 }
  override def main(): Any = {
    val temperature: Double = sense[java.lang.Double]("temperature")
    val thr = 0.5
    val id = includingSelf.minHoodSelector(nbr(temperature))(nbr(mid()))
    val candidate = branch(id == mid()) { T(5) <= 0 } { false }
    val clusters = cluster
      .input(temperature)
      .keyGenerator(mid())
      .process(id => input => broadcast(mid() == id, temperatureToBroadcast(id, input)))
      .insideIf(_ => myTemp => leaderTemp => Math.abs(myTemp - leaderTemp) <= thr)
      .candidateCondition { candidate }
      .disjoint()
    node.put("candidate", candidate)
    node.put("clusters", clusters.keySet)
  }
}

class TemperatureOverlapBasedProblem extends Libs {
  override def main(): Any = {
    val temperature: Double = sense[java.lang.Double]("temperature")
    val thr = 0.5
    val id = includingSelf.minHoodSelector(nbr(temperature))(nbr(mid()))
    val candidate = branch(id == mid()) { T(5) <= 0 } { false }
    val clusters = cluster
      .input(temperature)
      .keyGenerator(mid())
      .process(key => input => { broadcast(mid() == key, mux(mid() == key) { temperature } { -1 }) })
      .insideIf(_ => myTemp => leaderTemp => Math.abs(myTemp - leaderTemp) <= thr)
      .candidateCondition { candidate }
      .overlap()
    node.put("candidate", candidate)
    node.put("clusters", clusters.keySet)
  }
}

class ClusterBasedOnNumber extends Libs {
  override def main(): Any = {
    val temperature: Double = sense[java.lang.Double]("temperature")
    val id = includingSelf.minHoodSelector(nbr(temperature))(nbr(mid()))
    val candidate = id == mid()
    val howMany = 10
    val clusters = cluster
      .input {}
      .keyGenerator { mid() }
      .process { id => _ =>
        val potential = classicGradient(id == mid())
        val ids = C[Double, Map[ID, Double]](potential, _ ++ _, Map(mid() -> potential), Map.empty)
        val accepted = ids.toList.sortBy(_._2).take(howMany).toMap
        broadcast(id == mid(), accepted)
      }
      .insideIf(_ => _ => ids => ids.contains(mid()))
      .candidateCondition(candidate)
      .disjoint()

    node.put("candidate", candidate)
    node.put("clusters", clusters.keySet)
  }
}

class ClusterAdaptWithRange extends Libs {
  override def main(): Any = {}
}
