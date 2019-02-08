package master.project.task

import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingAdapter
import master.endpoint.{AppMethod, Endpoint, EndpointAnalyzer}
import master.gitlab.GitlabConfig
import master.mongodb.MongoUtil
import master.mongodb.endpoint.EndpointImpactMapPersistence.{LoadEndpointImpactMap, PersistEndpointImpactMap}
import master.mongodb.endpoint.EndpointImpactModel
import master.mongodb.version.VersionModel
import master.mongodb.version.VersionPersistence.LoadVersions
import master.project.{ExecApi, ExecUtil}
import master.project.task.EndpointAnalysisTaskManager.{EndpointAnalysisTaskCompleted, EndpointAnalysisTaskRequest}

/**
  * Companion object of [[EndpointAnalysisTaskManager]]
  */
object EndpointAnalysisTaskManager {
  case class EndpointAnalysisTaskRequest(pathToProject: String,
                                         versionName: Option[String] = None,
                                          pathToStaticAnalysisOption: Option[String] = None)
  case class EndpointAnalysisTaskCompleted()

  /**
    * Actor constructor of [[EndpointAnalysisTaskRequest]]
    * @param projectId [[String]] Project id
    * @param mongoManager [[ActorRef]] mongo manager reference for persistence tasks.
    * @param projectManager [[ActorRef]] project manager task which subscribes to this actor activities.
    * @return Configuration necessary to instantiate a [[EndpointAnalysisTaskRequest]] actor.
    */
  def props(projectId: String,
            mongoManager: ActorRef,
            gitlabConfig: GitlabConfig,
            projectManager: ActorRef): Props = {
    Props(new EndpointAnalysisTaskManager(projectId, mongoManager, gitlabConfig, projectManager,
      EndpointAnalyzer(), ExecUtil))
  }
}

/**
  * Actor representing a task manager for endpoint analysis. This actor receives the followings tasks:
  * [[EndpointAnalysisTaskRequest]] - Request for endpoint analysis of a single or all versions of a project.
  *
  * @param projectId [[String]] Project id
  * @param mongoManager [[ActorRef]] mongo manager reference for persistence tasks.
  * @param gitlabConfig [[GitlabConfig]] gitlab configuration properties, necessary for cloning projects.
  * @param projectManager [[ActorRef]] project manager task which subscribes to this actor activities.
  * @param endpointAnalyzer [[EndpointAnalyzer]] auxiliary class to help on getting the path to the static analysis.
  * @param execApi [[ExecApi]] facade api for passing commands to execution by the OS.
  */
class EndpointAnalysisTaskManager(projectId: String, mongoManager: ActorRef, gitlabConfig: GitlabConfig,
                                  projectManager: ActorRef, endpointAnalyzer: EndpointAnalyzer, execApi: ExecApi) extends Actor with ActorLogging {
  implicit val implicitLog: LoggingAdapter = log

  override def preStart(): Unit = {
    log.info("EndpointAnalysisTaskManager started")
  }

  override def postStop(): Unit ={
    log.info("Terminating EndpointAnalysisTaskManager")
  }

  /**
    * Clone the project from git for analysis purposes.
    * @param pathToProjectFolder [[String]] Filepath containing the projects folder.
    * @param versionName [[String]] versionName
    */
  def cloneProject(pathToProjectFolder: String, versionName: String): Unit = {
    // TODO: Change it to get url for different projects
    // Creating command subStrings
    val repoLocation = s"https://oauth2:${gitlabConfig.privateToken}@${gitlabConfig.repoAddress}"
    val specificBranch = s"-b $versionName"
    val targetFolder = execApi.changeSlashesToFileSeparator(s"$pathToProjectFolder/$versionName")

    val command = s"git clone $repoLocation $specificBranch $targetFolder"
    // Exec the command
    execApi.exec(command)
  }

  override def receive = {
    /**
      * Endpoint analysis for a single version.
      */
    case EndpointAnalysisTaskRequest(pathToProject, Some(versionName), pathToStaticAnalysisOption) =>
      val impactMap: Map[Endpoint, List[AppMethod]] = endpointAnalyzer.analyzeEndpoint(pathToProject, pathToStaticAnalysisOption)
      log.debug(s"Endpoint Impact Analysis finished, ${impactMap.keys.size} endpoints analyzed for versionName:$versionName")

      mongoManager ! LoadVersions(projectId, MongoUtil.singleValueObserver[VersionModel]( (version) => {
        val endpointImpactModel = analyzeVersionEndpoints(version, pathToProject, pathToStaticAnalysisOption)
        mongoManager ! PersistEndpointImpactMap(endpointImpactModel)
        projectManager ! EndpointAnalysisTaskCompleted()
      }), versionTagOption = Some(versionName))

    /**
      * Endpoint analysis for all the versions contained in the database.
      */
    case EndpointAnalysisTaskRequest(pathToProjectFolder, None, pathToStaticAnalysisOption) =>
      //Loading EndpointImpactMap - to filter out already analyzed versions.
      mongoManager ! LoadEndpointImpactMap(projectId, observer = MongoUtil.aggregatorObserver[EndpointImpactModel]( (endpointImpactModelList) => {
        //Loading Versions - For endpoint analysis
        mongoManager ! LoadVersions(projectId, MongoUtil.aggregatorObserver[VersionModel]( (versionList) => {
          // Filtering already analyzed versions.
          val filteredVersionList = versionList filter {(versionModel) =>
            !endpointImpactModelList.map(_.versionName).contains(versionModel.versionName)}

          filteredVersionList foreach { (version) =>
            val versionName = version.versionName
            val pathToProject = pathToProjectFolder + "/" + versionName

            if(!Files.exists(Paths.get(pathToProject))) {
              log.debug(s"NO FILE FOUND FOR VERSION:$versionName")
//              cloneProject(pathToProjectFolder, versionName)
            } else {
              val endpointImpactModel = analyzeVersionEndpoints(version, pathToProject, pathToStaticAnalysisOption)
              mongoManager ! PersistEndpointImpactMap(endpointImpactModel)
              projectManager ! EndpointAnalysisTaskCompleted()
            }
          }
        }))
      }))
  }

  /**
    *
    * @param versionModel [[VersionModel]] Version to have its endpoints analyzed.
    * @param pathToProject [[String]] file path to the project.
    * @param pathToStaticAnalysisOption [[Option]] Optional path to the static analysis file.
    * @return [[EndpointImpactModel]] The endpoints analyzed.
    */
  def analyzeVersionEndpoints(versionModel: VersionModel, pathToProject: String,
                              pathToStaticAnalysisOption: Option[String]): EndpointImpactModel = {
    val versionName = versionModel.versionName
    val impactMap = endpointAnalyzer.analyzeEndpoint(s"$pathToProject", pathToStaticAnalysisOption)
    log.debug(s"Endpoint Impact Analysis finished, ${impactMap.keys.size} endpoints analyzed for versionName:$versionName")

    val endpointList: List[Endpoint] = impactMap map {(kv) => kv._1.addImpactedMethodsList(kv._2)} toList

    EndpointImpactModel(projectId, versionName, endpointList)
  }
}