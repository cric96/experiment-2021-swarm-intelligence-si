package it.unibo.alchemist.model.implementation.actions

import it.unibo.alchemist.model.implementations.actions.AbstractAction
import it.unibo.alchemist.model.implementations.nodes.SimpleNodeManager
import it.unibo.alchemist.model.interfaces.{Action, Context, Environment, Node, Reaction}
import org.apache.commons.math3.random.RandomGenerator
import scala.jdk.CollectionConverters.ListHasAsScala

class RandomRemoveNode[T](val environment: Environment[T, _], val randomGenerator: RandomGenerator, node: Node[T])
    extends AbstractAction[T](node) {
  override def cloneAction(node: Node[T], reaction: Reaction[T]): Action[T] =
    new RandomRemoveNode[T](environment, randomGenerator, node)

  override def execute(): Unit = {
    val nodes = environment.getNodes.asScala.map(new SimpleNodeManager(_)).filterNot(_.node == node)
    val toRemove = randomGenerator.nextInt(nodes.size)
    environment.removeNode(nodes(toRemove).node)
  }

  override def getContext: Context = Context.GLOBAL
}
