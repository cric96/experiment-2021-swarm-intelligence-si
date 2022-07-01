package it.unibo.alchemist.model.implementation.actions

import it.unibo.alchemist.model.implementations.actions.{AbstractAction, RunScafiProgram}
import it.unibo.alchemist.model.implementations.nodes.{GenericNode, ScafiDevice, SimpleNodeManager}
import it.unibo.alchemist.model.interfaces._
import org.apache.commons.math3.random.RandomGenerator

import scala.jdk.CollectionConverters.ListHasAsScala

class RandomAddNode[T, P <: Position[P]](
  val environment: Environment[T, P],
  val randomGenerator: RandomGenerator,
  val node: Node[T],
  val connectionRadius: Double,
  val minDelta: Double
) extends AbstractAction[T](node) {
  override def cloneAction(node: Node[T], reaction: Reaction[T]): Action[T] =
    new RandomAddNode(environment, randomGenerator, node, connectionRadius, minDelta)

  override def execute(): Unit = {
    val nodes = environment.getNodes.asScala.map(new SimpleNodeManager(_)).filterNot(_.node == this.node)
    val nearTo = nodes(randomGenerator.nextInt(nodes.size))
    val node = nearTo.node.cloneNode(environment.getSimulation.getTime)
    val position = environment.getPosition(nearTo.node)
    val newCoordinates = position.getCoordinates
      .map(coordinate => coordinate + connectionRadius * randomGenerator.nextDouble() + minDelta)
      .map[Number](identity(_))
    environment.addNode(node, environment.makePosition(newCoordinates: _*))
  }

  override def getContext: Context = Context.GLOBAL
}
