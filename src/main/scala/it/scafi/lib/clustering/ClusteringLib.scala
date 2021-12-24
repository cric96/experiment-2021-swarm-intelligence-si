package it.scafi.lib.clustering
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
trait ClusteringLib extends ClusteringAbstraction with ClusteringDefinition with ClusteringBuilder {
  self: AggregateProgram with StandardSensors with BlockG with CustomSpawn with BlockC =>

  def emptyClustering[K, C]: Clustering.ClusteringDivision[K, C] = {
    val aux = new ClusteringFromContext[K, Any, Any, C](new ClusterContext[K, Any, Any, C]()) {
      override def apply(): ClusterDivision = emptyClusterDivision
    }
    aux.emptyClusterDivision
  }
}
