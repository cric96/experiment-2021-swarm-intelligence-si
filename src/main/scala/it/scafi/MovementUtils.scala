package it.scafi

import it.unibo.alchemist.model.interfaces.Position
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist.{P, ScafiAlchemistSupport}
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
import it.unibo.scafi.space.{Point2D, Point3D}
// from https://github.com/metaphori/experiment-2021-spatiotemporaltuples
trait MovementUtils {
  self: AggregateProgram with StandardSensors with ScafiAlchemistSupport =>
  sealed trait Zone
  case class CircularZone(center: (Double, Double), radius: Double) extends Zone
  case class RectangularZone(center: (Double, Double), width: Double, height: Double) extends Zone

  def rectangleWalk(
    p1: Point2D = Point2D(0, 0),
    p2: Point2D = Point2D(1000, 1000)
  ): Point2D = {
    val goal = randomPoint()
    ifClose(cropRectangle(goal, p1, p2))
  }

  def cropRectangle(goal: Point2D, rect1: Point2D, rect2: Point2D): Point2D = {
    Point2D(
      if (goal.x < rect1.x) rect1.x else if (goal.x > rect2.x) rect2.x else goal.x,
      if (goal.y < rect1.y) rect1.y else if (goal.y > rect2.x) rect2.y else goal.y
    )
  }

  def randomPoint(p: Point3D = currentPosition(), maxStep: Double = 25): Point2D = {
    Point2D(p.x + (maxStep * 2) * (nextRandom() - 0.5), p.y + (maxStep * 2) * (nextRandom() - 0.5))
  }

  def ifClose(goal: Point3D, dist: Double = 1): Point2D = {
    rep(goal)(g => if (currentPosition().distance(g) <= dist) goal else g)
  }

  def explore(zone: Zone, trajectoryTime: Int, reachGoalRange: Double = 0): P = {
    require(trajectoryTime > 0)
    val (position, _) = rep((randomCoordZone(zone), trajectoryTime)) {
      case (_, decay) if decay == 0 => (randomCoordZone(zone), trajectoryTime)
      case (goal, _) if goal.distance(currentPosition()) < reachGoalRange => (goal, 0)
      case (goal, decay) => (goal, decay - 1)
    }
    position
  }

  private def randomCoordZone(zone: Zone): Point2D = zone match {
    case CircularZone((cx, cy), radius) =>
      // val randomRadius = radius * nextRandom()
      // val theta = nextRandom() * 2 * math.Pi
      // Point2D(cx + randomRadius * math.cos(theta), cy + randomRadius * math.sin(theta))
      Point2D(cx + radius * positiveNegativeRandom(), cy + radius * positiveNegativeRandom())

    case RectangularZone((rx, ry), w, h) =>
      Point2D(rx + (w / 2) * positiveNegativeRandom(), ry + (h / 2) * positiveNegativeRandom())
  }

  private def positiveNegativeRandom(): Double = {
    val multi = if (nextRandom() < 0.5) 1 else -1
    multi * nextRandom()
  }

  implicit class RichPoint3D(p: Point3D) {
    def toAlchemistPosition: Position[_] =
      alchemistEnvironment.makePosition(p.x, p.y, p.z)
  }

  implicit def toPoint2D(p: Point3D): Point2D = Point2D(p.x, p.y)
}
