package master.research.version

import master.project.task.ResearchUnit

/**
  * Class representing a research unit for a project version.
  *
  * @param versionName [[String]] The version name.
  * @param bugQty [[Int]] The bug quantity.
  * @param totalUsage [[Int]] Total endpoint usage of the version.
  * @param timeSpentOnProduction [[Long]] Total time spent online in milliseconds.
  * @param oldInstructionCoverage [[Double]] The old instruction coverage, before applying the weight map.
  * @param newInstructionCoverage [[Double]] The new instruction coverage, after applying the weight map.
  * @param oldBranchCoverage [[Double]] The old branch coverage, before applying the weight map.
  * @param newBranchCoverage [[Double]] The new branch coverage, after applying the weight map.
  */
case class VersionResearchUnit(versionName: String,
                               bugQty: Int,
                               totalUsage: Int,
                               timeSpentOnProduction: Long,
                               oldInstructionCoverage: Double,
                               newInstructionCoverage: Double,
                               operationalInstructionCoverage: Double,
                               oldBranchCoverage: Double,
                               newBranchCoverage: Double,
                               operationalBranchCoverage: Double) extends ResearchUnit {

  /** @return Headers for CSV export */
  def headers: List[String] = List("name", "bugQty", "timeSpentOnProduction", "totalUsage", "oldInstrCov",
    "newInstrCov", "operInstrCov", "oldBranchCov", "newBranchCov", "operBranchCov")

  /** @return [[VersionResearchUnit]] mapped to a CSV format row */
  override def toRow: List[Any] = List(versionName, bugQty, timeSpentOnProduction,  totalUsage, oldInstructionCoverage,
    newInstructionCoverage, operationalInstructionCoverage, oldBranchCoverage, newBranchCoverage, operationalBranchCoverage)
}
