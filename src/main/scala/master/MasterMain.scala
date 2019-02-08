package master

import akka.actor.ActorSystem
import akka.event.{LoggingAdapter, NoLogging}
import master.endpoint.RestConfig
import master.gitlab.{GitlabConfig, GitlabManager}
import master.mongodb.{MongoConfig, MongoManager}
import master.project.ProjectManager._
import master.project.task.CoverageAnalysisTaskManager.CoverageAnalysisRequest
import master.project.task.ElasticTaskManager.ElasticAnalysisTask
import master.project.task.EndpointAnalysisTaskManager.EndpointAnalysisTaskRequest
import master.project.task.ResearchUnitManager.VersionResearchExtraction
import master.project.{ProjectConfig, ProjectManager}
import master.research.report.ReportGenerator
import master.util.ConfigHelper.getYamlConfigFile

object MasterMain {
  implicit val log: LoggingAdapter = NoLogging

  def main(args: Array[String]): Unit = {
    import ch.qos.logback.classic.{Level, LoggerContext}
    import org.slf4j.LoggerFactory

    val loggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    val mongoLogger = loggerContext.getLogger("org.mongodb.driver")
    mongoLogger.setLevel(Level.OFF)

    implicit val system: ActorSystem = ActorSystem("Scala-Project-Analyzer")

    // gitlabConfig file retrieval
    val gitlabConfigFile = "./src/main/resources/gitlabConfig.yaml"
    val gitlabConfig: GitlabConfig = getYamlConfigFile[GitlabConfig](gitlabConfigFile, classOf[GitlabConfig])
    // mongoConfig file retrieval
    val mongoConfigFile = "./src/main/resources/mongoConfig.yaml"
    val mongoConfig: MongoConfig = getYamlConfigFile[MongoConfig](mongoConfigFile, classOf[MongoConfig])
    // projectConfig file retrieval
    val projectConfigFile = "./src/main/resources/projectConfig.yaml"
    val projectConfig: ProjectConfig = getYamlConfigFile[ProjectConfig](projectConfigFile, classOf[ProjectConfig])
    // restConfig file retrieval
    val restConfigFile = "./src/main/resources/restConfig.yaml"
    val restConfig: RestConfig = getYamlConfigFile[RestConfig](restConfigFile, classOf[RestConfig])

    // Mounting restConfig for restManager
    val managerConfig = ManagerConfig(mongoConfig, gitlabConfig, projectConfig)

    val mongoManager = system.actorOf(MongoManager.props(managerConfig.mongoConfig), s"MongoManager-${projectConfig.projectId}")
    val gitlabManager = system.actorOf(GitlabManager.props(managerConfig.gitlabConfig), s"GitlabManager-${projectConfig.projectId}")

    val projectManager = system.actorOf(ProjectManager.props(managerConfig, projectConfig.projectId, mongoManager, gitlabManager),
      s"ProjectManager-${projectConfig.projectId}")
    //          projectManager ! LoadInitData()
    //          projectManager ! VersionAnalysis(true)
    //          projectManager ! JsonBugExtraction()
    //          projectManager ! BugAnalysis()
    //          projectManager ! VersionChangeAnalysis()
    //          projectManager ! CommitChangeAnalysis()
//              projectManager ! EndpointAnalysisTaskRequest(projectConfig.projectDir)
//              projectManager ! ElasticAnalysisTask()
//              projectManager ! CoverageAnalysisRequest(projectConfig.projectDir)
              projectManager ! VersionResearchExtraction(projectConfig.projectDir)
    //          projectManager ! MethodResearchExtraction(projectConfig.projectDir)

//              projectManager ! FullLoadTask()

//    val reportManager = system.actorOf(ReportGenerator.props(projectConfig.projectId, projectConfig, mongoManager, projectManager, gitlabManager),
//      s"ReportGenerator-${projectConfig.projectId}")
  }
}
