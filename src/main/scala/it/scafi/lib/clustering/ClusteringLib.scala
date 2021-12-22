package it.scafi.lib.clustering
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
trait ClusteringLib extends ClusteringAbstraction with ClusteringDefinition with ClusteringBuilder {
  self: AggregateProgram with StandardSensors with BlockG with CustomSpawn with BlockC =>
}
