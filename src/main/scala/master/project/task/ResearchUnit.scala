package master.project.task

abstract class ResearchUnit {

  /** @return Headers for CSV export */
  def headers: List[String]

  /** @return [[ResearchUnit]] mapped to a CSV format row */
  def toRow: List[Any]
}
