package it.casestudy

import breeze.linalg
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist.P
import it.unibo.scafi.space.Point3D

case class ClusterData[V: Numeric](point: P, data: V)(implicit ev: Double => V) {
  type BreezeVector = linalg.Vector[Double]
  private val BreezeVector = linalg.Vector
  protected[ClusterData] val underlyingRep: BreezeVector =
    BreezeVector(point.x, point.y, point.z, implicitly[Numeric[V]].toDouble(data))
  implicit protected[ClusterData] def fromUnderlyingRep(v: BreezeVector): ClusterData[V] = {
    val scalaRep = v.toScalaVector
    val Vector(x, y, z) = scalaRep.take(3)
    val data = scalaRep.last
    ClusterData[V](Point3D(x, y, z), data)
  }

  def min(other: ClusterData[V]): ClusterData[V] = linalg.min(this.underlyingRep, other.underlyingRep)

  def max(other: ClusterData[V]): ClusterData[V] = linalg.max(this.underlyingRep, other.underlyingRep)

  def +(other: ClusterData[V]): ClusterData[V] = this.underlyingRep + other.underlyingRep

  def -(other: ClusterData[V]): ClusterData[V] = this.underlyingRep - other.underlyingRep

  def /(scalar: Double): ClusterData[V] = this.underlyingRep / scalar

  def *(scalar: Double): ClusterData[V] = this.underlyingRep / scalar

  def distance(other: ClusterData[V]): Double =
    Math.sqrt(linalg.squaredDistance(this.underlyingRep, other.underlyingRep))
}

object ClusterData {
  implicit def clusterNumeric[V: Numeric]: Ordering[ClusterData[V]] = (x: ClusterData[V], y: ClusterData[V]) =>
    if (x.min(y) == x) { 1 }
    else if (x.min(y) != x) { -1 }
    else { 0 }
}
