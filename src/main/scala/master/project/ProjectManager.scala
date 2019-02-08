package master.project

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import master.bugzilla.BugzillaJsonExtractor
import master.gitlab.GitlabConfig
import master.mongodb.MongoConfig
import master.mongodb.bug.BugModel
import master.mongodb.bug.BugPersistence.PersistBugs
import master.project.ProjectManager._
import master.project.task.BugAnalysisTaskManager.{BugAnalysis, BugAnalysisTaskCompleted}
import master.project.task.ChangeAnalysisTaskManager.{CommitChangeAnalysis, VersionChangeAnalysis}
import master.project.task.CoverageAnalysisTaskManager.{CoverageAnalysisCompleted, CoverageAnalysisRequest}
import master.project.task.ElasticTaskAnalyzer.ElasticAnalysisCompleted
import master.project.task.ElasticTaskManager.ElasticAnalysisTask
import master.project.task.EndpointAnalysisTaskManager.{EndpointAnalysisTaskCompleted, EndpointAnalysisTaskRequest}
import master.project.task.InitDataTaskManager.{InitDataLoadTaskCompleted, LoadInitData, LoadTagData, TagDataLoadTaskCompleted}
import master.project.task.LogAnalysisTaskManager.LogAnalysis
import master.project.task.ResearchUnitManager.{MethodResearchExtraction, VersionResearchExtraction}
import master.project.task.VersionAnalysisTaskManager.{VersionAnalysis, VersionAnalysisTaskCompleted}
import master.project.task.change.ChangeAnalyzer.VersionChangeAnalysisTaskCompleted
import master.util.{SourceApi, SourceUtil}


object ProjectManager {
  case class ManagerConfig(mongoConfig: MongoConfig, gitlabConfig: GitlabConfig, projectConfig: ProjectConfig)
  case class FullLoadTask()
  case class JsonBugExtraction()
  case class TagDataLoadTask(tag: String)

  def defaultProjectActorConfigGenerator: (ManagerConfig, String, ActorRef, ActorRef, ActorRef) => ProjectActorConfig =
    (projectConfig: ManagerConfig, projectId: String, mongoManager: ActorRef, gitlabManager: ActorRef, projectManager: ActorRef) =>
      new ProjectActorConfig(projectConfig, projectId, mongoManager, gitlabManager, projectManager)

  def props(managerConfig: ManagerConfig, projectId: String, mongoManager: ActorRef, gitlabManager: ActorRef, sourceApi: SourceApi = SourceUtil,
            projectActorConfigGenerator: (ManagerConfig, String, ActorRef, ActorRef, ActorRef) => ProjectActorConfig
            = defaultProjectActorConfigGenerator): Props = {
    Props(new ProjectManager(managerConfig, projectId, mongoManager, gitlabManager, sourceApi,
      projectActorConfigGenerator)
    )
  }
}

class ProjectManager(managerConfig: ManagerConfig, projectId: String, mongoManager: ActorRef,
                     gitlabManager: ActorRef, sourceApi: SourceApi,
                     projectActorConfigGenerator: (ManagerConfig, String, ActorRef, ActorRef, ActorRef)
                       => ProjectActorConfig) extends Actor with ActorLogging {

  val projectActorConfig: ProjectActorConfig = projectActorConfigGenerator(managerConfig, projectId, mongoManager,
    gitlabManager, self)

  val ProjectActorFactory(bugAnalysisManager, changeAnalysisManager, versionAnalysisManager,
  initLoadManager, coverageAnalysisManager, logAnalyzerManager, endpointAnalyzerManager,
  elasticManager, researchManager) = projectActorConfig.createFactoryConfig(context)

  var projectConfig: ProjectConfig = managerConfig.projectConfig

  override def preStart(): Unit = log.info(s"Project Manager started with projectId:$projectId")

  override def postStop(): Unit = log.info("Terminating Project Manager")

  override def receive = waitingForTasks

  def fullLoad: Receive = {
    case InitDataLoadTaskCompleted() =>
      versionAnalysisManager ! VersionAnalysis(true)

    case VersionAnalysisTaskCompleted() =>
      log.info("Bug Extraction Task Started")
      val bugSource = sourceApi.getSource("bugs.json").get
      val bugList: List[BugModel] = BugzillaJsonExtractor.extract(bugSource) map {
        _.asModel()
      }
      mongoManager ! PersistBugs(bugList,
        callback = () => bugAnalysisManager ! BugAnalysis())
      log.info("Bug Extraction Task Complete")

    case BugAnalysisTaskCompleted() =>
      log.debug("BugAnalysisTaskCompleted -> VersionChangeAnalysis")
      changeAnalysisManager ! VersionChangeAnalysis()

    case VersionChangeAnalysisTaskCompleted() =>
      endpointAnalyzerManager ! EndpointAnalysisTaskRequest(projectConfig.projectDir)
      log.debug("BugAnalysisTaskCompleted -> VersionChangeAnalysis")

    case EndpointAnalysisTaskCompleted() =>
      log.debug("EndpointAnalysisTaskCompleted -> ElasticAnalysisTask")
      elasticManager ! ElasticAnalysisTask()

    case ElasticAnalysisCompleted() =>
      log.debug("ElasticAnalysisCompleted -> CoverageAnalysisRequest")
      coverageAnalysisManager ! CoverageAnalysisRequest(projectConfig.projectDir)

    case CoverageAnalysisCompleted() =>
      log.debug("CoverageAnalysisCompleted -> VersionResearchExtraction")
      versionAnalysisManager ! VersionResearchExtraction(projectConfig.projectDir)
      context.become(waitingForTasks)
  }

  def tagLoad(tag: String, sender: ActorRef): Receive = {
    case TagDataLoadTaskCompleted(_) =>
      changeAnalysisManager ! VersionChangeAnalysis(Some(tag))

    case VersionChangeAnalysisTaskCompleted() =>
      log.debug("VersionChangeAnalysisTaskCompleted -> EndpointAnalysisTaskRequest")
      endpointAnalyzerManager ! EndpointAnalysisTaskRequest(s"${projectConfig.projectDir}/$tag", Some(tag))

    case EndpointAnalysisTaskCompleted() =>
      log.debug("ElasticAnalysisCompleted -> CoverageAnalysisRequest")
      coverageAnalysisManager ! CoverageAnalysisRequest(s"${projectConfig.projectDir}/", Some(tag))

    case CoverageAnalysisCompleted() =>
      log.debug(s"Coverage analysis finished. $sender")
      sender ! TagDataLoadTaskCompleted(List.empty)
      context.become(waitingForTasks)
  }

  def waitingForTasks: Receive = {
    case FullLoadTask() =>
      initLoadManager ! LoadInitData()
      context.become(fullLoad)

    case TagDataLoadTask(tag: String) =>
      initLoadManager ! LoadTagData(tag)
      context.become(tagLoad(tag, sender()))

    case req@LoadInitData() =>
      log.info("Load Initial Data Task Started")
      initLoadManager ! req

    case req@VersionAnalysis(_) =>
      log.info("Version Analysis Task Started")
      versionAnalysisManager ! req

    case req@BugAnalysis(_,_) =>
      log.info("Bug Analysis Task Started")
      bugAnalysisManager ! req

    case req@CommitChangeAnalysis() =>
      log.info("Commit Change Analysis Task Started")
      changeAnalysisManager ! req

    case req@VersionChangeAnalysis(_) =>
      log.info("Version Change Analysis Task Started")
      changeAnalysisManager ! req

    case JsonBugExtraction() =>
      log.info("Bug Extraction Task Started")
      val bugSource = sourceApi.getSource("bugs.json").get
      val bugList: List[BugModel] = BugzillaJsonExtractor.extract(bugSource) map {
        _.asModel()
      }
      mongoManager ! PersistBugs(bugList,
        callback = () => self ! BugAnalysis())
      log.info("Bug Extraction Task Complete")

    case req@CoverageAnalysisRequest(_, _) =>
      log.info("Coverage Analysis Task Started")
      coverageAnalysisManager ! req

    case req@LogAnalysis(_) =>
      log.info("Log Analysis Task Started")
      logAnalyzerManager ! req

    case req@EndpointAnalysisTaskRequest(_, _, _) =>
      log.info("Endpoint Analysis Task Started")
      endpointAnalyzerManager ! req

    case req@ElasticAnalysisTask(_) =>
      log.info("Endpoint Analysis Task Started")
      elasticManager ! req

    case req@VersionResearchExtraction(_) =>
      log.info("Version Research Extraction Started")
      researchManager ! req

    case req@MethodResearchExtraction(_) =>
      log.info("Method Research Extraction Started")
      researchManager ! req

    case req@_ =>
      log.error(s"Project Manager don't know how to handle this request:$req")
  }
}
