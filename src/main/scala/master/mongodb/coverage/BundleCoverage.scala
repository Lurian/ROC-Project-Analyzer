package master.mongodb.coverage

import org.jacoco.core.analysis.IBundleCoverage

import scala.collection.JavaConverters._

object BundleCoverage {
  def apply(bundleCoverage: IBundleCoverage): BundleCoverage = {
    val packageCovList: List[PackageCoverage] = (bundleCoverage.getPackages asScala) map {(covPackage) => PackageCoverage(covPackage)} toList

    new BundleCoverage(bundleCoverage.getName,
      CoverageCounter(CoverageType.INSTRUCTION, bundleCoverage.getInstructionCounter),
      CoverageCounter(CoverageType.BRANCH, bundleCoverage.getBranchCounter),
      CoverageCounter(CoverageType.LINE, bundleCoverage.getLineCounter),
      CoverageCounter(CoverageType.COMPLEXITY, bundleCoverage.getComplexityCounter),
      CoverageCounter(CoverageType.METHOD, bundleCoverage.getMethodCounter),
      CoverageCounter(CoverageType.CLASS, bundleCoverage.getClassCounter),
      packageCovList)
  }

  def apply(name: String,
            instructionCounter: CoverageCounter,
            branchCounter: CoverageCounter,
            lineCounter: CoverageCounter,
            complexityCounter: CoverageCounter,
            methodCounter: CoverageCounter,
            classCounter: CoverageCounter,
            packages: List[PackageCoverage]): BundleCoverage = new BundleCoverage(name, instructionCounter, branchCounter, lineCounter, complexityCounter, methodCounter, classCounter, packages)
}

case class BundleCoverage(override val name: String,
                          override val instructionCounter: CoverageCounter,
                          override val branchCounter: CoverageCounter,
                          override val lineCounter: CoverageCounter,
                          override val complexityCounter: CoverageCounter,
                          override val methodCounter: CoverageCounter,
                          override val classCounter: CoverageCounter,
                          packageList: List[PackageCoverage]) extends CoverageNode(
  name,
  instructionCounter,
  branchCounter,
  lineCounter,
  complexityCounter,
  methodCounter,
  classCounter) {


  def applyWeightMap(weightMap: Map[String, Double]) : BundleCoverage = {
    val newPackageList : List[PackageCoverage] = packageList map { (packageCoverage) => packageCoverage.applyWeightMap(weightMap)}

    val newInstructionCounter = CoverageCounter(newPackageList map {_.instructionCounter}, CoverageType.INSTRUCTION)
    val newBranchCounter = CoverageCounter(newPackageList map {_.branchCounter}, CoverageType.BRANCH)
    val newLineCounter = CoverageCounter(newPackageList map {_.lineCounter}, CoverageType.LINE)
    val newComplexityCounter = CoverageCounter(newPackageList map {_.complexityCounter}, CoverageType.COMPLEXITY)
    val newMethodCounter = CoverageCounter(newPackageList map {_.methodCounter}, CoverageType.METHOD)
    val newClassCounter = CoverageCounter(newPackageList map {_.classCounter}, CoverageType.CLASS)

    BundleCoverage(
      this.name,
      newInstructionCounter,
      newBranchCounter,
      newLineCounter,
      newComplexityCounter,
      newMethodCounter,
      newClassCounter,
      newPackageList)
  }

  def getSignatureToInstrCounterMap(): Map[String, CoverageCounter] = {
    packageList flatMap {packageCoverage: PackageCoverage => packageCoverage.getSignatureToInstrCounterMap()} toMap
  }

  def getInstrCounterOfMethods(signatures: List[String]): CoverageCounter = {
    val coverageList: List[CoverageCounter] = packageList map { packageCoverage: PackageCoverage =>
      packageCoverage.getInstrCounterOfMethods(signatures)
    }
    CoverageCounter(coverageList, CoverageType.INSTRUCTION)
  }
}

