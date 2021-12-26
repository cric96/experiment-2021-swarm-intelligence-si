package it

package object casestudy {

  /**
   * Data class used to express the precision allowed in the approximation
   */
  case class Precision(p: Double)

  /**
   * Type enrichment for Double to have approximate equals (~=)
   * Usage:
   * implicit val precision = Precision(0.0001)
   * 0.001 ~= 0.0010000000001 true
   * 0.001 ~= 0.0011          false
   */
  implicit class DoubleWithApproximation(val d: Double) {
    def ~=(d2: Double)(implicit p: Precision): Boolean = (d - d2).abs < p.p
    def +-(d2: Double): Boolean = d.abs < d2
  }
}
