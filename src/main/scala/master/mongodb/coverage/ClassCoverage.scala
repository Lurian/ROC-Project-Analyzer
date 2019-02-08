package master.mongodb.coverage

import org.jacoco.core.analysis.IClassCoverage

import scala.collection.JavaConverters._

object ClassCoverage {
  def apply(classCoverage: IClassCoverage): ClassCoverage = {
   val methodCovList: List[MethodCoverage] = (classCoverage.getMethods asScala) map { (covClass) => MethodCoverage(covClass) } toList

    new ClassCoverage(classCoverage.getName,
      CoverageCounter(CoverageType.INSTRUCTION, classCoverage.getInstructionCounter),
      CoverageCounter(CoverageType.BRANCH, classCoverage.getBranchCounter),
      CoverageCounter(CoverageType.LINE, classCoverage.getLineCounter),
      CoverageCounter(CoverageType.COMPLEXITY, classCoverage.getComplexityCounter),
      CoverageCounter(CoverageType.METHOD, classCoverage.getMethodCounter),
      CoverageCounter(CoverageType.CLASS, classCoverage.getClassCounter),
      classCoverage.getId,
      classCoverage.isNoMatch,
      Option(classCoverage.getSignature),
      classCoverage.getSuperName,
      classCoverage.getInterfaceNames.toList,
      classCoverage.getPackageName,
      classCoverage.getSourceFileName,
      methodCovList)
  }
}

case class ClassCoverage(override val name: String,
                         override val instructionCounter: CoverageCounter,
                         override val branchCounter: CoverageCounter,
                         override val lineCounter: CoverageCounter,
                         override val complexityCounter: CoverageCounter,
                         override val methodCounter: CoverageCounter,
                         override val classCounter: CoverageCounter,
                         id: Long,
                         isNoMatch: Boolean,
                         signature: Option[String],
                         superName: String,
                         interfaceNames: List[String],
                         packageName: String,
                         sourceFileName: String,
                         methodList: List[MethodCoverage]) extends CoverageNode(
  name,
  instructionCounter,
  branchCounter,
  lineCounter,
  complexityCounter,
  methodCounter,
  classCounter) {

  def applyWeightMap(weightMap: Map[String, Double]): ClassCoverage = {
    val newMethodList : List[MethodCoverage] = methodList map {(methodCoverage) => methodCoverage.applyWeight(weightMap, this.name)}

    val newInstructionCounter = CoverageCounter(newMethodList map {_.instructionCounter}, CoverageType.INSTRUCTION)
    val newBranchCounter = CoverageCounter(newMethodList map {_.branchCounter}, CoverageType.BRANCH)
    val newLineCounter = CoverageCounter(newMethodList map {_.lineCounter}, CoverageType.LINE)
    val newComplexityCounter = CoverageCounter(newMethodList map {_.complexityCounter}, CoverageType.COMPLEXITY)
    val newMethodCounter = CoverageCounter(newMethodList map {_.methodCounter}, CoverageType.METHOD)
    val newClassCounter = CoverageCounter(newMethodList map {_.classCounter}, CoverageType.CLASS)

    ClassCoverage(
      this.name,
      newInstructionCounter,
      newBranchCounter,
      newLineCounter,
      newComplexityCounter,
      newMethodCounter,
      newClassCounter,
      this.id,
      this.isNoMatch,
      this.signature,
      this.superName,
      this.interfaceNames,
      this.packageName,
      this.sourceFileName,
      newMethodList)
  }

  def getSignatureToInstrCounterMap(): Map[String, CoverageCounter] = {
    methodList flatMap {methodCoverage => methodCoverage.getSignatureToInstrCounterMap(this.name)} toMap
  }

  def getInstrCounterOfMethods(signatures: List[String]): CoverageCounter = {
    val coverageList: List[CoverageCounter] = methodList flatMap {methodCoverage: MethodCoverage =>
      methodCoverage.getInstrCounterOfMethods(signatures, this.name)
    }
    CoverageCounter(coverageList, CoverageType.INSTRUCTION)
  }
}
