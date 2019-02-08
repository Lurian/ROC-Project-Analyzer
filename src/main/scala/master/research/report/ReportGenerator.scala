package master.research.report

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props}
import akka.event.LoggingAdapter
import master.util.ConfigHelper.getYamlConfigFile
import master.endpoint.{EndpointAnalyzer, ImpactedMethodsCalculator}
import master.gitlab.GitlabConfig
import master.mongodb.MongoUtil
import master.mongodb.elastic.ElasticVersionAnalysis
import master.mongodb.elastic.ElasticVersionAnalysisPersistence.LoadElasticVersionAnalysis
import master.mongodb.version.VersionModel
import master.mongodb.version.VersionPersistence.LoadVersions
import master.project.ProjectManager.TagDataLoadTask
import master.project.task.InitDataTaskManager.TagDataLoadTaskCompleted
import master.project.task.ResearchUnitManager
import master.project.{ExecApi, ExecUtil, ProjectConfig}
import master.research.report.EndpointCoverageExtractor.EndpointCoverage
import master.research.report.VersionCoverageExtractor.VersionCoverage
import master.research.version.{VersionResearchUnit, VersionResearchUnitExtractor}
import master.util.{FileSource, SourceApi, SourceUtil}

object ReportGenerator {
  case class VersionRequest(tag: String)
  def props(projectId: String, projectConfig: ProjectConfig,
            mongoManager: ActorRef, projectManager: ActorRef, gitlabManager: ActorRef, sourceApi: SourceApi = SourceUtil,
            execApi: ExecApi = ExecUtil): Props = {
    Props( new ReportGenerator(projectId, projectConfig, mongoManager, projectManager, gitlabManager, sourceApi, execApi))
  }
}

class ReportGenerator(projectId: String, projectConfig: ProjectConfig, mongoManager: ActorRef, projectManager: ActorRef,
                      gitlabManager: ActorRef, sourceApi: SourceApi, execApi: ExecApi)
  extends Actor with ActorLogging {
  import ReportGenerator._

  implicit def implicitLog: LoggingAdapter = log

  override def receive: Receive = waitingForVersionReportRequest

  def getCloneUrl(privateToken: String, repoAddress: String): String = {
    s"https://$privateToken@$repoAddress"
  }

  def cloneVersion(projectFolder: String, tag: String): Unit = {
    val gitlabConfigFile = "./src/main/resources/gitlabConfig.yaml"
    val gitlabConfig: GitlabConfig = getYamlConfigFile[GitlabConfig](gitlabConfigFile, classOf[GitlabConfig])

    val cloneUrl = getCloneUrl(gitlabConfig.privateToken, gitlabConfig.repoAddress)
    execApi.exec(s"git clone $cloneUrl -b $tag")
//    gitlabManager ! downloadTarget(tag, projectFolder)
  }

  def checkForContentCompletion(targetVersion: String, previousVersion: String, newVersionReport: VersionReport): Unit = {
    if(newVersionReport.isComplete){
      log.debug(newVersionReport.getReport)
    } else {
      context.become(waitingForContent(targetVersion, previousVersion, newVersionReport))
    }
  }

  def waitingForContent(targetVersion: String, previousVersion: String, versionReport: VersionReport): Receive = {
    case newContent@EndpointCoverage(`targetVersion`, _) =>
      val newVersionReport = VersionReport(Some(newContent), versionReport.previousVersionEndpointCoverage,
        versionReport.targetVersionResearchUnit, versionReport.previousVersionResearchUnit,
        versionReport.targetVersionCoverage, versionReport.previousVersionCoverage)
      checkForContentCompletion(targetVersion, previousVersion, newVersionReport)

    case newContent@EndpointCoverage(`previousVersion`, _) =>
      val newVersionReport = VersionReport(versionReport.targetVersionEndpointCoverage, Some(newContent),
        versionReport.targetVersionResearchUnit, versionReport.previousVersionResearchUnit,
        versionReport.targetVersionCoverage, versionReport.previousVersionCoverage)
      checkForContentCompletion(targetVersion, previousVersion, newVersionReport)

    case newContent@VersionResearchUnit(`targetVersion`, _, _, _, _, _, _, _, _, _) =>
      val newVersionReport = VersionReport(versionReport.targetVersionEndpointCoverage, versionReport.previousVersionEndpointCoverage,
        Some(newContent), versionReport.previousVersionResearchUnit,
        versionReport.targetVersionCoverage, versionReport.previousVersionCoverage)
      checkForContentCompletion(targetVersion, previousVersion, newVersionReport)

    case newContent@VersionResearchUnit(`previousVersion`, _, _, _, _, _, _, _, _, _) =>
      val newVersionReport = VersionReport(versionReport.targetVersionEndpointCoverage, versionReport.previousVersionEndpointCoverage,
        versionReport.targetVersionResearchUnit, Some(newContent),
        versionReport.targetVersionCoverage, versionReport.previousVersionCoverage)
      checkForContentCompletion(targetVersion, previousVersion, newVersionReport)

    case newContent@VersionCoverage(`targetVersion`, _, _, _) =>
      val newVersionReport = VersionReport(versionReport.targetVersionEndpointCoverage, versionReport.previousVersionEndpointCoverage,
        versionReport.targetVersionResearchUnit, versionReport.previousVersionResearchUnit,
        Some(newContent), versionReport.previousVersionCoverage)
      checkForContentCompletion(targetVersion, previousVersion, newVersionReport)

    case newContent@VersionCoverage(`previousVersion`, _, _, _) =>
      val newVersionReport = VersionReport(versionReport.targetVersionEndpointCoverage, versionReport.previousVersionEndpointCoverage,
        versionReport.targetVersionResearchUnit, versionReport.previousVersionResearchUnit,
        versionReport.targetVersionCoverage, Some(newContent))
      checkForContentCompletion(targetVersion, previousVersion, newVersionReport)
  }

  def waitingForCustomElastic(versionModel: VersionModel): Receive = {
    case analysisList@List(_:ElasticVersionAnalysis, _*) =>
      val elasticVersionAnalysis = analysisList.head.asInstanceOf[ElasticVersionAnalysis]
      log.debug(elasticVersionAnalysis.versionName + "<-- Elastic")

      val pathToStaticAnalysis = ResearchUnitManager.getPathToStaticAnalysis(projectConfig.projectDir, versionModel.versionName)
      lazy val staticAnalysisSource = sourceApi.getSource(EndpointAnalyzer().pathToStaticAnalysisBuilder(pathToStaticAnalysis)).get
      val impactedMethodsCalculator = ImpactedMethodsCalculator(staticAnalysisSource)

      val importantEndpoints = elasticVersionAnalysis.endpointUsageList.sortBy(endpoint => endpoint.usage.getOrElse(0)).takeRight(30)
        .map(endpoint => {
          val methodTree = impactedMethodsCalculator.calculateImpactedMethods(endpoint.restMethod)
          endpoint.addImpactedMethodsList(methodTree.getAllMethodsReached.toList)
        })

      // Endpoint Coverage Extractor
      context.actorOf(EndpointCoverageExtractor.props(projectId, versionModel.versionName, importantEndpoints,
      mongoManager, self), s"EndpointCoverageExtractor-${versionModel.versionName}")
      context.actorOf(EndpointCoverageExtractor.props(projectId, versionModel.previousVersionName.get, importantEndpoints,
      mongoManager, self), s"EndpointCoverageExtractor-${versionModel.previousVersionName.get}")
      // Returning EndpointCoverage

      // Operational Coverage
      val childFactoryFun = (versionModel: VersionModel, staticAnalysisSource: FileSource, parent: ActorRef) =>
        (f: ActorRefFactory) => f.actorOf(VersionResearchUnitExtractor.props(projectId, versionModel, staticAnalysisSource,
          mongoManager, parent, SourceUtil))

      val dummyPreviousVersion = VersionModel(projectId, versionModel.previousVersionName.get, None, None, new Date(),
          None, List.empty, List.empty)
      childFactoryFun(versionModel, staticAnalysisSource, self)(context)
      childFactoryFun(dummyPreviousVersion, staticAnalysisSource, self)(context)
      // Returning VersionResearchUnit

      // Version Coverage Extractor
      context.actorOf(VersionCoverageExtractor.props(projectId, versionModel.versionName, elasticVersionAnalysis,
        staticAnalysisSource, mongoManager, self), s"VersionCoverageExtractor-${versionModel.versionName}")
      context.actorOf(VersionCoverageExtractor.props(projectId, versionModel.previousVersionName.get, elasticVersionAnalysis,
        staticAnalysisSource, mongoManager, self), s"VersionCoverageExtractor-${versionModel.previousVersionName.get}")
      // Returning VersionCoverage
      context.become(waitingForContent(versionModel.versionName, versionModel.previousVersionName.get,
        VersionReport(None, None, None, None, None, None)))

    case other@_ =>
      log.error(s"Dam other: $other")
  }

  def waitingForTagLoad(tag: String): Receive = {
    case TagDataLoadTaskCompleted(_) =>
      mongoManager ! LoadVersions(projectId, versionTagOption = Some(tag), observer = MongoUtil.sendBackObserver[VersionModel](self))
    case versionList@List(_:VersionModel, _*) =>
      val versionModel= versionList.head.asInstanceOf[VersionModel]
      log.debug(versionModel.versionName + "<-- version report")
      mongoManager ! LoadElasticVersionAnalysis(projectId, Some(versionModel.previousVersionName.get),
        MongoUtil.sendBackObserver[ElasticVersionAnalysis](self))
      context.become(waitingForCustomElastic(versionModel))
    case other@_ =>
      log.error(other.toString)
  }

  def waitingForVersionReportRequest: Receive = {
    case VersionRequest(tag: String) =>
      //      cloneVersion(projectFolder, tag); Isso pode ser feito manualmente
      projectManager ! TagDataLoadTask(tag)
      context.become(waitingForTagLoad(tag))
  }
}

//
//Report Generator - Version Name
//  - Load Version
//
//  - Retrieve Custom ElasticAnalysis with historical
//
//  - Past Endpoint Ranking - Actor
//    Coverage Under Endpoint - Actual Version Impact
//    Coverage Under Endpoint - Past Version Impact
//  - Operational Coverage
//    VRU - Actual
//    VRU - Past
//  - Impacted Version
//    Signature Extraction
//    Coverage Under Version

