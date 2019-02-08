package master.research.version

import master.project.task.ResearchUnit

/**
  * Class representing a research unit for a project version.
  *
  * @param signature [[String]] Method signature.
  * @param weight [[Double]] Weight applied to coverage values.
  */
case class WeightMapUnit(signature: String,
                         weight: Double) extends ResearchUnit {

  /** @return Headers for CSV export */
  def headers: List[String] = List("signature", "weight")

  /** @return [[WeightMapUnit]] mapped to a CSV format row */
  override def toRow: List[Any] = List(signature, weight)
}
