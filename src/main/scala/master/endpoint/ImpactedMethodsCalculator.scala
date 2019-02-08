package master.endpoint

import master.endpoint.usage.MethodRelationship
import master.project.ProjectConfig
import master.util.ConfigHelper.getYamlConfigFile
import master.util.FileSource

object ImpactedMethodsCalculator {

  private val clazzRegex = """C:(.*)\s(.*)""".r
  private val methodRegex = """M:(.*):(.*)\s\(.\)(.*):(.*)""".r

  val projectConfigFile = "./src/main/resources/projectConfig.yaml"
  val projectConfig: ProjectConfig = getYamlConfigFile[ProjectConfig](projectConfigFile, classOf[ProjectConfig])

  private def toMethodRelationship(string: String): Option[MethodRelationship] = {
    string match {
      case clazzRegex(_, _) => None
      case methodRegex(sourceClass, sourceMethod, targetClass, targetMethod) =>
        val fromMethod = AppMethod(sourceClass, sourceMethod)
        val toMethod = AppMethod(targetClass, targetMethod)
        Some(MethodRelationship(fromMethod, toMethod))
      case _ =>  None
    }
  }

  private def projectMethods: MethodRelationship => Boolean = (appMethod: MethodRelationship) =>
    appMethod.targetMethod.classDeclaration.contains(projectConfig.rootPackage)

  private def sourceMethod: MethodRelationship => AppMethod = (methodR: MethodRelationship) => { methodR.sourceMethod }

  def apply(staticAnalysisSource: FileSource): ImpactedMethodsCalculator = {
    val methodList: List[MethodRelationship] = (staticAnalysisSource.getLines map toMethodRelationship).flatten.toList
    val methodMap: Map[AppMethod, List[MethodRelationship]] = methodList filter projectMethods groupBy sourceMethod
    new ImpactedMethodsCalculator(methodMap)
  }
}

class ImpactedMethodsCalculator(methodMap: Map[AppMethod, List[MethodRelationship]]) {

  def heuristicSignatureFind(rootMethodList: List[AppMethod]): List[(AppMethod, List[AppMethod])] = {
    val analysisMethodList: List[AppMethod] = methodMap.keys.toList
    rootMethodList map { (rootMethod) => rootMethod -> analysisMethodList.filter(_.equals(rootMethod)) }
  }

  def calculateImpactedMethods(rootMethod: AppMethod): MethodTree = {
    MethodTree(rootMethod, methodMap, Set(rootMethod))
  }
}
