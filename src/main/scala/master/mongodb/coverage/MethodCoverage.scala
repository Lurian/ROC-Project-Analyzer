package master.mongodb.coverage

import org.jacoco.core.analysis.IMethodCoverage

object MethodCoverage {
  def apply(methodCoverage: IMethodCoverage): MethodCoverage = {
    new MethodCoverage(methodCoverage.getName,
      CoverageCounter(CoverageType.INSTRUCTION, methodCoverage.getInstructionCounter),
      CoverageCounter(CoverageType.BRANCH, methodCoverage.getBranchCounter),
      CoverageCounter(CoverageType.LINE, methodCoverage.getLineCounter),
      CoverageCounter(CoverageType.COMPLEXITY, methodCoverage.getComplexityCounter),
      CoverageCounter(CoverageType.METHOD, methodCoverage.getMethodCounter),
      CoverageCounter(CoverageType.CLASS, methodCoverage.getClassCounter),
      methodCoverage.getDesc,
      Option(methodCoverage.getSignature))
  }
}

case class MethodCoverage(override val name: String,
                          override val instructionCounter: CoverageCounter,
                          override val branchCounter: CoverageCounter,
                          override val lineCounter: CoverageCounter,
                          override val complexityCounter: CoverageCounter,
                          override val methodCounter: CoverageCounter,
                          override val classCounter: CoverageCounter,
                          descriptor: String,
                          signature: Option[String]) extends CoverageNode(
  name,
  instructionCounter,
  branchCounter,
  lineCounter,
  complexityCounter,
  methodCounter,
  classCounter) {

  def applyWeight(weightMap: Map[String, Double], className: String): MethodCoverage = {
    val filteredKeys = weightMap filterKeys { (methodSignature) =>
      val signature = s"$className/${this.name}".replace('/','.')
      methodSignature == signature
    }

    val weight = filteredKeys match {
      case map if map.size == 1 => filteredKeys.head._2
      case map =>
        if (map.size > 1) {
          println(s"########### FOUND ${map.size} ############")
          println(map)
          println(s"METHOD: $className.${this.name}")
          println(s"########## @@@@@@@@@@@@@@   #############")
        }
        1
    }

    val newInstructionCounter = this.instructionCounter.applyElementWeight(weight)
    val newBranchCounter = this.branchCounter.applyElementWeight(weight)
    val newLineCounter = this.lineCounter.applyElementWeight(weight)
    val newComplexityCounter = this.complexityCounter.applyElementWeight(weight)
    val newMethodCounter = this.methodCounter.applyElementWeight(weight)
    val newClassCounter = this.classCounter.applyElementWeight(weight)

    MethodCoverage(
      this.name,
      newInstructionCounter,
      newBranchCounter,
      newLineCounter,
      newComplexityCounter,
      newMethodCounter,
      newClassCounter,
      this.descriptor,
      this.signature)
  }

  def getInstrCounterOfMethods(signatures: List[String], className: String): Option[CoverageCounter] = {
    val methodSignature = s"$className/${this.name}".replace('/','.')
    if(signatures.contains(methodSignature))
      Some(this.instructionCounter)
    else
      None
  }

  def getSignatureToInstrCounterMap(className: String): Map[String, CoverageCounter] = {
    val signature = s"$className/${this.name}".replace('/','.')
    Map(signature -> instructionCounter)
  }
}
