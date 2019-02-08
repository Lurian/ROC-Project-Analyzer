package master.mongodb.coverage

import org.jacoco.core.analysis.ICounter

object CoverageCounter {
  def apply(coverageType: String, counter: ICounter): CoverageCounter =
    new CoverageCounter(coverageType,
      counter.getTotalCount,
      counter.getCoveredCount,
      counter.getMissedCount,
      counter.getCoveredRatio,
      counter.getMissedRatio)

  def apply(coverageType: String, totalCount: Int, coveredCount: Int, missedCount: Int): CoverageCounter =
    new CoverageCounter(coverageType,
      totalCount,
      coveredCount,
      missedCount,
      coveredCount.toDouble / totalCount.toDouble,
      missedCount.toDouble / totalCount.toDouble)

  def apply(counterList: List[CoverageCounter], coverageType: String): CoverageCounter = {
    val totalCounter = (counterList map {_.totalCount}).sum
    val coveredCount = (counterList map {_.coveredCount}).sum
    val missedCount = (counterList map {_.missedCount}).sum
    CoverageCounter(coverageType, totalCounter, coveredCount, missedCount)
  }
}

case class CoverageCounter(coverageType: String,
                           totalCount: Int,
                           coveredCount: Int,
                           missedCount: Int,
                           coveredRatio: Double,
                           missedRatio: Double) {

  def applyElementWeight(weight: Double): CoverageCounter = {
    val newTotalCount =  (this.totalCount * weight).toInt
    val newCoveredCount =  (this.coveredCount * weight).toInt
    val newMissedCount =  newTotalCount - newCoveredCount

    CoverageCounter(this.coverageType,
      newTotalCount,
      newCoveredCount,
      newMissedCount)
  }
}
