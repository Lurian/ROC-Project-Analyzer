package master.mongodb.coverage

import java.util.Date

case class CoverageModel(projectId: String, creationTime: Date, identifier: String, bundleCoverage: BundleCoverage) {

  def getMethodCoverage(signature: String): Option[MethodCoverage] = {
    val classCoverageList: List[ClassCoverage] = bundleCoverage.packageList.flatMap(_.classList)
    val className: String = signature.split('.').reverse.tail.head
    val methodName: String = signature.split('.').reverse.head.split('(').head

    val allPossibleMethodsList: List[MethodCoverage] = classCoverageList.flatMap(_.methodList)
    println(methodName)
    val filteredMethods = allPossibleMethodsList.filter(_.name.equals(methodName))

    // Did it found only one method?
    if(filteredMethods.size == 1) return Some(filteredMethods.head)

    // If not, filter by class
    val filteredClasses = classCoverageList.filter { (classCoverage) => {
      val coverageClassName = classCoverage.name.split('/').reverse.head
      coverageClassName.equals(className)
    }}

    val filteredPossibleMethodsList: List[MethodCoverage] = filteredClasses.flatMap(_.methodList)
    val filteredMethodsAndByClass = filteredPossibleMethodsList.filter(_.name.equals(methodName))

    // Did it found only one method?
    if(filteredMethodsAndByClass.size == 1) return Some(filteredMethodsAndByClass.head)

    // If not, check on the super classes
    val superClassNames = filteredClasses.map(_.superName)
    val superClasses = classCoverageList.filter { (classCoverage) => {
      val coverageClassName = classCoverage.name.split('/').reverse.head
      superClassNames.exists( (superClassName) => coverageClassName.equals(superClassName))
    }}

    val filteredSuperClassMethodsList: List[MethodCoverage] = superClasses.flatMap(_.methodList)
    val filteredMethodsAndBySuperClass = filteredSuperClassMethodsList.filter(_.name.equals(methodName))

    // Did it found only one method?
    if(filteredMethodsAndBySuperClass.size == 1) return Some(filteredMethodsAndBySuperClass.head)

    // If not, print for debug
    println("#@@@@@@@###@#@#@#")
    println(s"signature:$signature")
    println(s"className:$className")
    println(s"methodName:$methodName")
    println(s"filteredClasses:${filteredClasses.map(_.name)}")
    println(s"filteredClassesSuperName:${filteredClasses.map(_.superName)}")
    println(s"filteredMethods:${filteredMethods.map(_.name)}")
    println(s"filteredMethodsAndByClass:${filteredMethodsAndByClass.map(_.name)}")
    println(s"allPossibleMethodsList: ${allPossibleMethodsList.map(_.name)}")
    println("#@@@@@@@###@#@#@#")
    None
  }

  def applyWeightMap(weightMap: Map[String, Double]): CoverageModel =
    CoverageModel(projectId, creationTime, identifier, bundleCoverage.applyWeightMap(weightMap))

  def getSignatureToInstrCounterMap(): Map[String, CoverageCounter] = bundleCoverage.getSignatureToInstrCounterMap()

  def getInstrCounterOfMethods(signatures: List[String]): CoverageCounter = bundleCoverage.getInstrCounterOfMethods(signatures)
}