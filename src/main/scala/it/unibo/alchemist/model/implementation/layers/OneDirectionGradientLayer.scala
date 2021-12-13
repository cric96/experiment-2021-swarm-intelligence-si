package it.unibo.alchemist.model.implementation.layers

import it.unibo.alchemist.model.interfaces.{Layer, Position, Position2D}

/**
 * Utility for create a layer with decrease/increase uniformly towards a direction.
 * The value at a given point (x, y) is evaluated as:
 * baseValue + (x - centerX) * dx + (y - centerY) * dy
 * @param baseValue the value at (centerX, centerY)
 * @param centerX the central point of the field (x coordinate)
 * @param centerY the central point of the field (y coordinate)
 * @param dx delta x value of the increasing/decreasing field
 * @param dy delta y value of the increasing/decreasing field
 * @tparam P
 */
class OneDirectionGradientLayer[P <: Position2D[P]](
  baseValue: Double,
  centerX: Double,
  centerY: Double,
  dx: Double,
  dy: Double
) extends Layer[Double, Position2D[P]] {

  override def getValue(p: Position2D[P]): Double = {
    val x = p.getX
    val y = p.getY
    val distanceX = x - centerX
    val distanceY = y - centerY

    baseValue + (distanceX * dx) + (distanceY * dy)
  }
}
