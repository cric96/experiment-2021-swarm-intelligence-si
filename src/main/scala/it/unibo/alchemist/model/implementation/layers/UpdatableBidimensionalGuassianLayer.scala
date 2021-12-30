package it.unibo.alchemist.model.implementation.layers

import it.unibo.alchemist.model.implementations.layers.BidimensionalGaussianLayer
import it.unibo.alchemist.model.interfaces.Position2D
import it.unibo.alchemist.model.math.BidimensionalGaussian

class UpdatableBidimensionalGuassianLayer[P <: Position2D[P]](
  val baseline: Double = 0,
  var centerX: Double,
  var centerY: Double,
  var norm: Double,
  var sigmaX: Double,
  var sigmaY: Double
) extends BidimensionalGaussianLayer[P](baseline, centerX, centerY, norm, sigmaX, sigmaY) {
  def function =
    new BidimensionalGaussian(norm, centerX, centerY, sigmaX, sigmaY)

  override def getValue(p: P): java.lang.Double = baseline + function.value(p.getX, p.getY)
  // side effects...
  def deltaChangeSize(dx: Double): Unit = {
    sigmaY += dx
    sigmaX += dx
  }

  override def toString =
    s"UpdatableBidimensionalGuassianLayer(baseline=$baseline, centerX=$centerX, centerY=$centerY, norm=$norm, sigmaX=$sigmaX, sigmaY=$sigmaY)"
}
