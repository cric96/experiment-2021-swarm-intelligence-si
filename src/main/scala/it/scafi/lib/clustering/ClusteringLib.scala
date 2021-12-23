package it.scafi.lib.clustering
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
trait ClusteringLib extends ClusteringAbstraction with ClusteringDefinition with ClusteringBuilder {
  self: AggregateProgram with StandardSensors with BlockG with CustomSpawn with BlockC =>

  def emptyClustering[K, D]: Clustering.ClusteringDivision[K, D] = {
    val aux = new ClusteringFromContext[K, Any, D](new ClusterContext[K, Any, D]()) {
      override def apply(): ClusterDivision = emptyClusterDivision
    }
    aux.emptyClusterDivision
  }
}
