package it

package object casestudy {
  case class Precision(p: Double)

  implicit class DoubleWithAlmost(val d: Double) {
    def ~=(d2: Double)(implicit p: Precision): Boolean = (d - d2).abs < p.p
    def +-(d2: Double): Boolean = d.abs < d2
  }
}
