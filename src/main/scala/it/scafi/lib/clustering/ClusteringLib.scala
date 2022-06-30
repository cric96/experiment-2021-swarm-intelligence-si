package it.scafi.lib.clustering
import it.scafi.lib.BlocksWithShare
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

/**
 * Aggregator of Clustering(Abstraction, Builder, Definition) forming the ClusteringLib.
 */
trait ClusteringLib extends ClusteringAbstraction with ClusteringDefinition with ClusteringBuilder {
  self: AggregateProgram with StandardSensors with BlockG with CustomSpawn with BlockC with BlocksWithShare =>

  /**
   * returns a empty clustering process division (i.e. both cluster maps are empty)
   */
  def emptyClusterDivision[K, C]: Clustering.ClusteringDivision[K, C] = {
    // helper to extract the emptyClusterDivision method
    val aux = new ClusteringFromContext[K, Any, Any, C](new ClusterContext[K, Any, Any, C]()) {
      override def apply(): ClusterDivision = emptyClusterDivision
    }
    aux.emptyClusterDivision
  }
}
