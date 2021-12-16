package it.unibo.alchemist.boundary.gui.effects
import it.unibo.alchemist.boundary.gui.effects.DrawClusterEffect._
import it.unibo.alchemist.boundary.wormhole.interfaces.Wormhole2D
import it.unibo.alchemist.model.implementations.nodes.SimpleNodeManager
import it.unibo.alchemist.model.interfaces.{Environment, Node, Position2D}
import it.unibo.alchemist.scala.ScalaInterpreter

import java.awt.{Color, Graphics2D, LinearGradientPaint, Paint, Point, Shape}
import java.awt.geom.{AffineTransform, Rectangle2D}

class DrawClusterEffect extends Effect {
  var totalAgentNumber = 0
  override def apply[T, P <: Position2D[P]](
    g: Graphics2D,
    node: Node[T],
    env: Environment[T, P],
    wormhole: Wormhole2D[P]
  ): Unit = if (env.getNodes.contains(node)) {
    updateAgentNumber(env)
    val nodePosition: P = env.getPosition(node)
    val viewPoint: Point = wormhole.getViewPoint(nodePosition)
    val manager = new SimpleNodeManager[T](node)
    val (x, y) = (viewPoint.x, viewPoint.y)
    val transform = getTransform(x, y, wormhole.getZoom / clusterSize)
    val shape = new Rectangle2D.Double(
      minBound,
      minBound,
      areaSize,
      areaSize
    )
    val transformedShape = transform.createTransformedShape(shape)
    val paint = getPaintFrom(manager, transformedShape)
    g.setPaint(paint)
    g.fill(transformedShape)
  }

  override def getColorSummary: Color = Color.BLACK

  private def updateAgentNumber(env: Environment[_, _]): Unit = {
    totalAgentNumber = env.getNodes.size()
  }

  private def getPaintFrom(node: SimpleNodeManager[_], shape: Shape): Paint = {
    val clusters = if (node.has("clusters")) { node.get[Set[Int]]("clusters") }
    else { Set.empty[Int] }
    clusters.toList.sorted match {
      case Nil => Color.black
      case color :: Nil => colorFromId(color)
      case clusters =>
        val colors: Array[Color] = clusters.map(colorFromId).toArray
        val fractions = 1.0f / (colors.length)
        val colorFraction = 0.9 * fractions
        val blackFraction = (0.1 * fractions) / colors.length
        val allColor = colors.flatMap(c => List(Color.BLACK, c))
        val bound = shape.getBounds2D
        val fractionsArray: Array[Float] = LazyList
          .iterate(List(0.0f, blackFraction)) { case black :: color :: Nil =>
            List(color + colorFraction, color + colorFraction + blackFraction)
          }
          .take(colors.length)
          .flatten
          .toArray
          .map(_.toFloat)
        new LinearGradientPaint(
          bound.getMinX.toFloat,
          bound.getMinY.toFloat,
          bound.getMaxX.toFloat,
          bound.getMaxY.toFloat,
          fractionsArray,
          allColor
        )
    }
  }

  private def colorFromId(id: Int): Color = Color.getHSBColor(id / totalAgentNumber.toFloat, 1f, 1f)

  private def getTransform(x: Int, y: Int, zoom: Double): AffineTransform = {
    val transform = new AffineTransform()
    transform.translate(x, y)
    transform.scale(zoom, zoom)
    transform
  }
}

object DrawClusterEffect {
  val minBound: Float = -1
  val maxBound: Float = 1
  val clusterSize: Float = 8
  def areaSize = maxBound - minBound
}
