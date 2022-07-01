package it.scafi.lib.clustering
import it.scafi.lib.BlocksWithShare
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

/**
 * Aggregator of Clustering(Abstraction, Builder, Definition) forming the ClusteringLib.
 */
trait ClusteringLib extends ClusteringAbstraction with ClusteringDefinition with ClusteringBuilder {
  self: AggregateProgram
    with StandardSensors
    with BlockG
    with CustomSpawn
    with BlockC
    with BlockT
    with BlocksWithShare
    with ScafiAlchemistSupport =>

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

  /**
   * Utils to create watch dogs that look to non rechable leader
   */
  def lastWillWatchDog[K, D](clusters: Map[K, D], lastWillCount: Int, idExtractor: K => ID): Set[K] = {
    clusters.keys
      .map(key => {
        align(key) { k =>
          val leaderBeats = GWithShare(idExtractor(k) == mid(), roundCounter(), identity[Long], nbrRange)
          val (leaderNotReachable, _) = rep((false, leaderBeats)) { case (_, old) =>
            (branch(old == leaderBeats) { T(lastWillCount) == 0 } { false }, leaderBeats)
          }
          (key, leaderNotReachable)
        }
      })
      .filter { case (_, noSignal) => noSignal }
      .map { case (id, _) => id }
      .toSet
  }

}
