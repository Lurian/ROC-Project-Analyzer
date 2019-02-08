package master.mongodb.coverage

import org.jacoco.core.analysis.IPackageCoverage

import scala.collection.JavaConverters._

object PackageCoverage {
  def apply(packageCoverage: IPackageCoverage): PackageCoverage = {
    val classCovList: List[ClassCoverage] = (packageCoverage.getClasses asScala) map { (covClass) => ClassCoverage(covClass) } toList

    new PackageCoverage(packageCoverage.getName,
      CoverageCounter(CoverageType.INSTRUCTION, packageCoverage.getInstructionCounter),
      CoverageCounter(CoverageType.BRANCH, packageCoverage.getBranchCounter),
      CoverageCounter(CoverageType.LINE, packageCoverage.getLineCounter),
      CoverageCounter(CoverageType.COMPLEXITY, packageCoverage.getComplexityCounter),
      CoverageCounter(CoverageType.METHOD, packageCoverage.getMethodCounter),
      CoverageCounter(CoverageType.CLASS, packageCoverage.getClassCounter),
      classCovList)
  }
}

case class PackageCoverage(override val name: String,
                           override val instructionCounter: CoverageCounter,
                           override val branchCounter: CoverageCounter,
                           override val lineCounter: CoverageCounter,
                           override val complexityCounter: CoverageCounter,
                           override val methodCounter: CoverageCounter,
                           override val classCounter: CoverageCounter,
                           classList: List[ClassCoverage]) extends CoverageNode(
  name,
  instructionCounter,
  branchCounter,
  lineCounter,
  complexityCounter,
  methodCounter,
  classCounter) {

  def applyWeightMap(weightMap: Map[String, Double]): PackageCoverage = {
    val newClassList : List[ClassCoverage] = classList map {(classCoverage) => classCoverage.applyWeightMap(weightMap)}

    val newInstructionCounter = CoverageCounter(newClassList map {_.instructionCounter}, CoverageType.INSTRUCTION)
    val newBranchCounter = CoverageCounter(newClassList map {_.branchCounter}, CoverageType.BRANCH)
    val newLineCounter = CoverageCounter(newClassList map {_.lineCounter}, CoverageType.LINE)
    val newComplexityCounter = CoverageCounter(newClassList map {_.complexityCounter}, CoverageType.COMPLEXITY)
    val newMethodCounter = CoverageCounter(newClassList map {_.methodCounter}, CoverageType.METHOD)
    val newClassCounter = CoverageCounter(newClassList map {_.classCounter}, CoverageType.CLASS)

    PackageCoverage(
      this.name,
      newInstructionCounter,
      newBranchCounter,
      newLineCounter,
      newComplexityCounter,
      newMethodCounter,
      newClassCounter,
      newClassList)
  }

  def getSignatureToInstrCounterMap(): Map[String, CoverageCounter] = {
    classList flatMap {classCoverage: ClassCoverage => classCoverage.getSignatureToInstrCounterMap()} toMap
  }

  def getInstrCounterOfMethods(signatures: List[String]): CoverageCounter = {
    val coverageList: List[CoverageCounter] = classList map {
      classCoverage: ClassCoverage => classCoverage.getInstrCounterOfMethods(signatures)
    }
    CoverageCounter(coverageList, CoverageType.INSTRUCTION)
  }
}
