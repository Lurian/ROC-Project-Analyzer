package master.endpoint


import akka.event.LoggingAdapter
import master.project.{ExecApi, ExecUtil, ProjectConfig}
import master.util.ConfigHelper.getYamlConfigFile
import master.util.{SourceApi, SourceUtil}

import scala.collection.JavaConverters._
import scala.xml.transform.{RewriteRule, RuleTransformer}
import scala.xml.{Elem, Node}

/**
  * Companion object which manages the EndpointAnalyzer single instance of the application.
  */
object EndpointAnalyzer {
  var endpointAnalyzerInstance: Option[EndpointAnalyzer] = None
  val projectConfigFile = "./src/main/resources/projectConfig.yaml"
  val projectConfig: ProjectConfig = getYamlConfigFile[ProjectConfig](projectConfigFile, classOf[ProjectConfig])

  val restConfigFile = "./src/main/resources/restConfig.yaml"
  val restConfig: RestConfig = getYamlConfigFile[RestConfig](restConfigFile, classOf[RestConfig])

  def restChecker(versionName: String): RestChecker = new RestChecker(versionName, projectConfig, restConfig)
  def apply(sourceApi: SourceApi = SourceUtil, execApi: ExecApi = ExecUtil,
            restCheckerFactory: String => RestCheckerApi = restChecker, projectConfig: ProjectConfig = projectConfig): EndpointAnalyzer = {
    if(endpointAnalyzerInstance.isDefined) {
      endpointAnalyzerInstance.get.sourceApi = sourceApi
      endpointAnalyzerInstance.get.execApi = execApi
      endpointAnalyzerInstance.get.restCheckerFactory = restCheckerFactory
    } else endpointAnalyzerInstance = Some(new EndpointAnalyzer(sourceApi, execApi, restCheckerFactory, projectConfig))
    endpointAnalyzerInstance.get
  }
}

/**
  * Auxiliary class which manages the findings of endpoints of a Java project which uses JAX-RS.
  * @param sourceApi Facade for communicating with the OS File System.
  */
class EndpointAnalyzer(var sourceApi: SourceApi, var execApi: ExecApi, var restCheckerFactory: String => RestCheckerApi,
                       projectConfig: ProjectConfig) {

  lazy val pathToJavaCallgraphJar: String = execApi.changeSlashesToFileSeparator("./src/main/resources/javacg-0.1-SNAPSHOT-static.jar")

  def pathToStaticAnalysisBuilder(pathToProject: String): String = execApi.changeSlashesToFileSeparator(s"$pathToProject/target/staticAnalysis.txt")

  /**
    * Uses the java-callgraph https://github.com/gousiosg/java-callgraph/blob/master/README.md
    * library to statically calculate the method relationships of the project
    * @param pathToProject File path to the project.
    */
  def doStaticAnalysis(pathToProject: String): Unit = {
    val pathToJar =  execApi.changeSlashesToFileSeparator(pathToProject + projectConfig.jarSubPath)
    if(!sourceApi.fileExists(pathToJar)) doJarPackaging(pathToProject)

    val staticAnalysisExecCommand = s"java -jar $pathToJavaCallgraphJar $pathToJar"
    val staticAnalysisText: String = execApi.execReturn(staticAnalysisExecCommand)
    val pathToStaticAnalysis: String = pathToStaticAnalysisBuilder(pathToProject)
    sourceApi.writeToFile(staticAnalysisText, pathToStaticAnalysis)
  }

  /**
    * Jar package the project to be used in the method relationship static analysis.
    * @param pathToProject file path to the project to be packaged.
    */
  def doJarPackaging(pathToProject: String): Unit = {
    val pathToPom = execApi.changeSlashesToFileSeparator(s"$pathToProject/pom.xml")
    modifyPomPackagingToJar(pathToPom)
    val jarPackagingCommand = s"mvn -f $pathToPom package -DskipTests"
    execApi.exec(jarPackagingCommand)
  }

  /**
    * Modify the pom.xml contained in the project directory. This modifies the '<packaging>' clause
    * to 'jar'.
    * @param pathToPom File path to the project directory
    */
  def modifyPomPackagingToJar(pathToPom: String): Unit = {
    val xml = sourceApi.loadXML(pathToPom)
    val jarPackagingTransformer = new RewriteRule {
      override def transform(n: Node): Seq[Node] = n match {
        case elem: Elem if elem.label == "packaging" =>
          <packaging>jar</packaging>
        case _ => n
      }
    }
    val transformer = new RuleTransformer(jarPackagingTransformer)
    val newXml = transformer(xml)

    sourceApi.saveXML(pathToPom, newXml)
  }

  /**
    * Analyze all methods impacted by the endpoints of a version. The methods impacted are all the methods that are possible to call
    * within all the possible executions of one endpoint method.
    * @param pathToProject Path to the project, necessary to calculate method relationships.
    * @param pathToStaticAnalysisOption [[Option]] To receive a different static analysis path instead of the default one. (/target/staticAnalysis.txt)
    * @param log [[LoggingAdapter]] to log messages as the actor using this object.
    * @return [[Map]] A map from [[Endpoint]] -> [[List]] of [[AppMethod]], this represent the list of impacted methods of an endpoint
    */
  def analyzeEndpoint(pathToProject: String,
                      pathToStaticAnalysisOption: Option[String])(implicit log: LoggingAdapter) : Map[Endpoint, List[AppMethod]] = {
    val pathToStaticAnalysis = pathToStaticAnalysisOption.getOrElse(pathToStaticAnalysisBuilder(pathToProject))

    log.debug(s"pathToProject = $pathToProject")
    log.debug("Checking if static analysis exists...")
    if(!sourceApi.fileExists(pathToStaticAnalysis)) {
      log.debug("It doesn't, doing analysis now...")
      doStaticAnalysis(pathToProject)
    }

    log.debug("Calculating endpoints of the project.")
    val endpointList: List[Endpoint] = getAllEndpoints(pathToProject); log.debug(s"${endpointList.size} endpoints found.")

    log.debug(s"Starting impacted methods analysis")
    val versionImpactedMethodsCalculator = ImpactedMethodsCalculator(sourceApi.getSource(pathToStaticAnalysis).get)
    val heuristicMethodFindMap = versionImpactedMethodsCalculator.heuristicSignatureFind(endpointList map {_.restMethod})

    log.debug(s"Checking heuristic findings")
    val everyEndpointHaveASingleMethodRelated = heuristicMethodFindMap forall {_._2.size <= 1}
    if (everyEndpointHaveASingleMethodRelated) {
      var count = 1
      endpointList map { endpoint =>
        log.debug(s"$count/${endpointList.size}"); count = count + 1
        val reachedMethods = versionImpactedMethodsCalculator.calculateImpactedMethods(endpoint.restMethod).getAllMethodsReached
        endpoint ->  reachedMethods.toList
      } toMap
    } else {
      // TODO: This should export to JSON in case of failure and try to import every time it executes
      heuristicMethodFindMap filter {
        _._2.size > 1
      } foreach println
      throw new RuntimeException("heuristicMethodFindMap found more than one option for some root endpoint method")
    }
  }

  /**
    * Get all the [[Endpoint]] of the project using [[RestChecker]]
    * @param pathToProject Path to the project.
    * @return [[List]] of endpoints.
    */
  def getAllEndpoints(pathToProject: String): List[Endpoint] = {
    // Calculating endpoint list with RestChecker
    val restChecker: RestCheckerApi = restCheckerFactory(pathToProject)
    val javaList: List[JavaEndpoint] = restChecker.getEndpointsFromDir.asScala.toList
    javaList map { Endpoint(_)}
  }
}
