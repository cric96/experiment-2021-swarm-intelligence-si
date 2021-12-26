package it.casestudy

import breeze.linalg
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist.P
import it.unibo.scafi.space.Point3D

import scala.language.implicitConversions

/**
 * A Data class used to express a spatial data.
 * It has a position (point) and a data perceived in that point (data)
 * @param point where the data is located
 * @param data the value perceived. In the implicit context, a Numeric[Data] should exist
 * @param ev implicit conversion from Double to V 
 * @tparam V the type of the data perceived.
 */
case class SpatialData[V: Numeric](point: P, data: V)(implicit ev: Double => V) {
  /* Internal representation of the spatial data: a tensor */
  type BreezeVector = linalg.Vector[Double]
  private val BreezeVector = linalg.Vector
  protected[SpatialData] val underlyingRep: BreezeVector =
    BreezeVector(point.x, point.y, point.z, implicitly[Numeric[V]].toDouble(data))
  implicit protected[SpatialData] def fromUnderlyingRep(v: BreezeVector): SpatialData[V] = {
    val scalaRep = v.toScalaVector
    val Vector(x, y, z) = scalaRep.take(3)
    val data = scalaRep.last
    SpatialData[V](Point3D(x, y, z), data)
  }
  /* Some matemathical operations */
  def min(other: SpatialData[V]): SpatialData[V] = linalg.min(this.underlyingRep, other.underlyingRep)

  def max(other: SpatialData[V]): SpatialData[V] = linalg.max(this.underlyingRep, other.underlyingRep)

  def +(other: SpatialData[V]): SpatialData[V] = this.underlyingRep + other.underlyingRep

  def -(other: SpatialData[V]): SpatialData[V] = this.underlyingRep - other.underlyingRep

  def /(scalar: Double): SpatialData[V] = this.underlyingRep / scalar

  def *(scalar: Double): SpatialData[V] = this.underlyingRep * scalar

  /**
   * Euclidean distance between two spatial data
   * @param other the target value by which we have to compute the distance
   * @return the distance between this and other
   */
  def distance(other: SpatialData[V]): Double =
    Math.sqrt(linalg.squaredDistance(this.underlyingRep, other.underlyingRep))
}

object SpatialData {
  implicit def clusterNumeric[V: Numeric]: Ordering[SpatialData[V]] = (x: SpatialData[V], y: SpatialData[V]) =>
    if (x.min(y) == x) { 1 }
    else if (x.min(y) != x) { -1 }
    else { 0 }
}
