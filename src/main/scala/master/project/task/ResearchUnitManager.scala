package master.project.task

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, OneForOneStrategy, Props, Stash, SupervisorStrategy}
import akka.event.LoggingAdapter
import master.mongodb.MongoUtil
import master.mongodb.version.VersionModel
import master.mongodb.version.VersionPersistence.LoadVersions
import master.project.task.ResearchUnitManager.{MethodResearchExtraction, VersionResearchExtraction}
import master.research.method.MethodUnitManager
import master.research.version.VersionUnitManager
import master.util.SourceApi

import scala.language.postfixOps

/**
  * Companion Object for [[ResearchUnitManager]]
  */
object ResearchUnitManager {
  def getPathToStaticAnalysis(pathToProjects: String, versionName: String) = s"$pathToProjects/$versionName"

  case class GetCurrentWorkerMap()

  case class GetDoneList()

  case class GetRemainingList()

  case class VersionResearchExtraction(pathToProjects: String)

  case class MethodResearchExtraction(pathToProjects: String)

  //TODO: update this scaladoc
  /**
    * Actor constructor for [[ResearchUnitManager]]
    *
    * @param projectId      [[String]] Id of the project being analyzed.
    * @param mongoManager   [[ActorRef]] of the persistence manager.
    * @param projectManager [[ActorRef]] manager of the actor being created
    * @return A config object for a [[ResearchUnitManager]] actor.
    */
  def props(projectId: String,
            versionFactoryFun: (String, List[VersionModel], ActorRef) => ActorRefFactory => ActorRef,
            methodVersionFun: (String, List[VersionModel], ActorRef) => ActorRefFactory => ActorRef,
            numberOfWorkers: Int = defaultNumberOfWorkers,
            mongoManager: ActorRef,
            gitlabManager: ActorRef,
            projectManager: ActorRef,
            sourceApi: SourceApi): Props = {
    Props(new ResearchUnitManager(projectId, numberOfWorkers, versionFactoryFun, methodVersionFun,
      mongoManager, gitlabManager, projectManager, sourceApi))
  }

  def defaultNumberOfWorkers = 5

  //TODO: update this scaladoc
  /**
    * Creates a [[ActorRefFactory]] function for a [[VersionUnitManager]]
    *
    * @param projectId     [[String]] Id of the project being analyzed.
    * @param mongoManager  [[ActorRef]] of the persistence manager.
    * @param gitlabManager [[ActorRef]] of the gitlab manager.
    * @return
    */
  def versionUnitManagerFactory(projectId: String, mongoManager: ActorRef, gitlabManager: ActorRef, sourceApi: SourceApi):
  (String, List[VersionModel], ActorRef) ⇒ ActorRefFactory ⇒ ActorRef = {
    val childPropsFun = (pathToProjects: String, filteredVersionList: List[VersionModel], parent: ActorRef) =>
      (f: ActorRefFactory) => {
        val VRUEChildFactory = VersionUnitManager.versionResearchUnitExtractorChildFactory(projectId, mongoManager)

        f.actorOf(VersionUnitManager.props(projectId, pathToProjects, filteredVersionList, VRUEChildFactory,
          defaultNumberOfWorkers, sourceApi, mongoManager, gitlabManager, parent), s"VersionUnitManager-$projectId")
      }
    childPropsFun
  }

  /**
    * Creates a [[ActorRefFactory]] function for a [[MethodUnitManager]]
    *
    * @param projectId     [[String]] Id of the project being analyzed.
    * @param mongoManager  [[ActorRef]] of the persistence manager.
    * @param gitlabManager [[ActorRef]] of the gitlab manager.
    * @return
    */
  def methodUnitManagerFactory(projectId: String, mongoManager: ActorRef, gitlabManager: ActorRef, sourceApi: SourceApi):
  (String, List[VersionModel], ActorRef) ⇒ ActorRefFactory ⇒ ActorRef = {
    val childPropsFun = (pathToProjects: String, filteredVersionList: List[VersionModel], parent: ActorRef) =>
      (f: ActorRefFactory) => {
        val MRUEChildFactory = MethodUnitManager.methodResearchUnitExtractorChildFactory(projectId, mongoManager, gitlabManager)

        f.actorOf(MethodUnitManager.props(projectId, pathToProjects, filteredVersionList, MRUEChildFactory,
          defaultNumberOfWorkers, sourceApi, mongoManager, gitlabManager, parent), s"MethodUnitManager-$projectId")
      }
    childPropsFun
  }
}

/**
  * This actor manage all research unit creation. Using several router of other actor it sends request for these actors and
  * keep track of their success
  *
  * @param projectId      [[String]] Id of the project being analyzed.
  * @param mongoManager   [[ActorRef]] of the persistence manager.
  * @param gitlabManager  [[ActorRef]] gitlab manager.
  * @param projectManager [[ActorRef]] manager of the actor.
  */
class ResearchUnitManager(projectId: String, val numberOfWorkers: Int,
                          VMUFactory: (String, List[VersionModel], ActorRef) ⇒ ActorRefFactory ⇒ ActorRef,
                          MMUFactory: (String, List[VersionModel], ActorRef) ⇒ ActorRefFactory ⇒ ActorRef,
                          mongoManager: ActorRef, gitlabManager: ActorRef, projectManager: ActorRef, sourceApi: SourceApi
                         ) extends Actor with ActorLogging with Stash {
  implicit val implicitLog: LoggingAdapter = log

  override val supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy() { case _: Exception => Stop }

  override def preStart(): Unit = {
    log.info("ResearchUnitManager started")
  }

  override def postStop(): Unit = {
    log.info("Terminating ResearchUnitManager")
  }

  override def receive = waitingForTask

  /**
    * Initial Mailbox State of the ResearchUnitManager
    */
  private def waitingForTask: Receive = {
    case task@VersionResearchExtraction(pathToProjects) =>
      log.debug("Version Research Extraction request received")
      mongoManager ! LoadVersions(projectId, observer = MongoUtil.sendBackObserver[VersionModel](self))
      context.become(waitingForVersionLoad(pathToProjects, Left(task)))
    case task@MethodResearchExtraction(pathToProjects) =>
      log.debug("Method Research Extraction request received")
      mongoManager ! LoadVersions(projectId, observer = MongoUtil.sendBackObserver[VersionModel](self))
      context.become(waitingForVersionLoad(pathToProjects, Right(task)))
    case msg@_ => log.error(s"Unexpected msg:$msg")
  }

  /**
    * Waiting for the project version load.
    *
    * @param pathToProjects Path to projects to be passed to the next workflow step.
    * @param request        request received to be passed to the next workflow step.
    */
  def waitingForVersionLoad(pathToProjects: String,
                            request: Either[VersionResearchExtraction, MethodResearchExtraction]): Receive = {
    case list: List[_] if list.isEmpty =>
      log.error(s"No VersionModels found for projectId:$projectId")
      unstashAll()
      context.become(waitingForTask)
    case list: List[_] =>
      log.debug("VersionList received")
      val versionList = list.asInstanceOf[List[VersionModel]]
      val filteredVersionList = versionList.filter((version) => {
        val pathToStaticAnalysis = s"$pathToProjects/${version.versionName}"
        sourceApi.fileExists(pathToStaticAnalysis)
      })
      log.debug(filteredVersionList.map(_.versionName).toString())
      log.debug(s"${versionList.size - filteredVersionList.size} versions removed due to project not found")
      if (filteredVersionList.isEmpty) {
        log.error("No Project Found for Extraction")
        context.become(waitingForTask)
      }

      request match {
        case Left(versionReq) =>
          implicit val VersionResearchExtraction(pathToProjects: String) = versionReq
          VMUFactory(pathToProjects, filteredVersionList, self)(context)
        case Right(methodReq) =>
          implicit val MethodResearchExtraction(pathToProjects: String) = methodReq
          MMUFactory(pathToProjects, filteredVersionList, self)(context)
      }
      unstashAll()
      context.become(waitingForTask)
    case msg@_ => log.debug(s"Stashing msg:$msg"); stash()
  }
}
