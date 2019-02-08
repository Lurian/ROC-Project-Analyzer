package master.research.method

import master.project.task.ResearchUnit

/**
  * Class representing a research unit for a project version.
  *
  * @param methodSignature [[String]] The signature of the method.
  * @param versionName [[String]] The version name.
  * @param bugQty [[Int]] The bug quantity.
  * @param totalUsage [[Int]] Total endpoint usage of the version.
  * @param timeSpentOnProduction [[Long]] Total time spent online in milliseconds.
  * @param instructionCoverage [[Double]] The instruction coverage.
  * @param branchCoverage [[Double]] The branch coverage.
  */
case class MethodResearchUnit(methodSignature: String,
                              versionName: String,
                              bugQty: Int,
                              totalUsage: Int,
                              timeSpentOnProduction: Long,
                              instructionCoverage: Double,
                              branchCoverage: Double) extends ResearchUnit {

  /** @return Headers for CSV export */
  def headers: List[String] = List("signature", "versionName", "bugQty", "timeSpentOnProduction", "totalUsage", "instrCov", "branchCov")

  /** @return [[MethodResearchUnit]] mapped to a CSV format row */
  def toRow: List[Any] = List(methodSignature, versionName, bugQty, timeSpentOnProduction,  totalUsage, instructionCoverage, branchCoverage)
}

