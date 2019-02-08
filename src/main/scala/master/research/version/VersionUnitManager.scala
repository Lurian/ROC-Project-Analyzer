package master.research.version

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, OneForOneStrategy, PoisonPill, Props, Stash, SupervisorStrategy, Terminated}
import master.endpoint.EndpointAnalyzer
import master.mongodb.version.VersionModel
import master.project.task.ResearchUnitManager
import master.project.task.ResearchUnitManager.{GetCurrentWorkerMap, GetDoneList, GetRemainingList, VersionResearchExtraction}
import master.research.method.MethodUnitManager
import master.util.{FileSource, SourceApi, SourceUtil}


object VersionUnitManager {
  /**
    * Actor constructor for [[MethodUnitManager]]
    *
    * @param projectId           [[String]] Id of the project being analyzed.
    * @param pathToProjects      [[String]] File path to the directory containing the projects to be analyzed.
    * @param filteredVersionList [[List]] of [[VersionModel]] filtered by the ones that have project files accessible.
    * @param childFactoryFun     [[Function]] That receives a [[VersionModel]], a [[FileSource]] and a [[ActorRef]] and
    *                            returns a [[ActorRefFactory]] capable of creating a [[ActorRef]] worker to extract the
    *                            [[VersionResearchUnit]] linked to the first parameters.
    * @param numberOfWorkers     [[Int]] Number of child workers to be created in parallel
    * @param sourceApi           [[SourceApi]] to be used when communicating with the OS File System
    * @param mongoManager        [[ActorRef]] of the persistence manager.
    * @param gitlabManager       [[ActorRef]] of the gitlab manager.
    * @param researchManager     [[ActorRef]] manager of the actor being created
    * @return A config object for a [[ResearchUnitManager]] actor.
    */
  def props(projectId: String,
            pathToProjects: String,
            filteredVersionList: List[VersionModel],
            childFactoryFun: (VersionModel, FileSource, ActorRef) => ActorRefFactory => ActorRef,
            numberOfWorkers: Int = ResearchUnitManager.defaultNumberOfWorkers,
            sourceApi: SourceApi,
            mongoManager: ActorRef,
            gitlabManager: ActorRef,
            researchManager: ActorRef): Props = {
    Props(new VersionUnitManager(projectId, pathToProjects, filteredVersionList, childFactoryFun, numberOfWorkers,
      sourceApi, mongoManager, gitlabManager, researchManager, EndpointAnalyzer()))
  }

  /**
    * Creates a [[ActorRefFactory]] function for a [[VersionResearchUnitExtractor]]
    *
    * @param projectId    [[String]] Id of the project being analyzed.
    * @param mongoManager [[ActorRef]] of the persistence manager.
    * @return
    */
  def versionResearchUnitExtractorChildFactory(projectId: String, mongoManager: ActorRef): (VersionModel, FileSource, ActorRef) ⇒ ActorRefFactory ⇒ ActorRef = {
    val childPropsFun = (versionModel: VersionModel, staticAnalysisSource: FileSource, parent: ActorRef) =>
      (f: ActorRefFactory) => f.actorOf(VersionResearchUnitExtractor.props(projectId, versionModel, staticAnalysisSource,
        mongoManager, parent, SourceUtil))
    childPropsFun
  }
}

/**
  *
  * @param projectId           [[String]] Id of the project being analyzed.
  * @param pathToProjects      [[String]] File path to the directory containing the projects to be analyzed.
  * @param filteredVersionList [[List]] of [[VersionModel]] filtered by the ones that have project files accessible.
  * @param childFactoryFun     [[Function]] That receives a [[VersionModel]], a [[FileSource]] and a [[ActorRef]] and
  *                            returns a [[ActorRefFactory]] capable of creating a [[ActorRef]] worker to extract the
  *                            [[VersionResearchUnit]] linked to the first parameters.
  * @param numberOfWorkers     [[Int]] Number of child workers to be created in parallel
  * @param sourceApi           [[SourceApi]] to be used when communicating with the OS File System
  * @param mongoManager        [[ActorRef]] of the persistence manager.
  * @param gitlabManager       [[ActorRef]] of the gitlab manager.
  * @param researchManager     [[ActorRef]] manager of the actor being created.
  * @param endpointAnalyzer    [[EndpointAnalyzer]] auxiliary class to help on getting the path to the static analysis.
  */
class VersionUnitManager(projectId: String, pathToProjects: String, filteredVersionList: List[VersionModel],
                         childFactoryFun: (VersionModel, FileSource, ActorRef) => ActorRefFactory => ActorRef,
                         numberOfWorkers: Int, sourceApi: SourceApi, mongoManager: ActorRef,
                         gitlabManager: ActorRef, researchManager: ActorRef, endpointAnalyzer: EndpointAnalyzer) extends Actor with ActorLogging with Stash {
  implicit val selfRef: ActorRef = self

  override val supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy() { case _: Exception => Stop }

  override def preStart(): Unit = {
    log.debug(s"Starting VersionUnitManager with projectId:$projectId")
    triggerInitialVersionExtraction()
  }

  override def receive: Receive = {
    case _ => log.error("Message received before pre-start")
  }

  /**
    * Mapper from [[VersionModel]] to Signature -> [[ActorRef]]
    *
    * @param versionModel [[VersionModel]] to be mapped
    * @return Signature -> [[ActorRef]]
    */
  def toSignatureAndVersionWorker(versionModel: VersionModel): (String, ActorRef) = {
    val pathToStaticAnalysis = ResearchUnitManager.getPathToStaticAnalysis(pathToProjects, versionModel.versionName)
    lazy val staticAnalysisSource = sourceApi.getSource(endpointAnalyzer.pathToStaticAnalysisBuilder(pathToStaticAnalysis)).get
    val r = createVersionResearchUnitExtractor(versionModel, staticAnalysisSource)
    versionModel.versionName -> r
  }

  /**
    * Trigger the initial Version Extraction using the default number of workers to concurrent extract data.
    */
  def triggerInitialVersionExtraction(): Unit = {
    if (filteredVersionList.isEmpty) context.stop(self)
    val initialVersions = filteredVersionList.take(numberOfWorkers)
    val workersMap: Map[String, ActorRef] = initialVersions map toSignatureAndVersionWorker toMap

    context.become(waitingForVersionExtractionResponses(workersMap, List.empty, filteredVersionList.drop(numberOfWorkers)))
  }

  /**
    * Create a new actor for version research unit extraction. Automatically send it a request for extraction.
    *
    * @param versionModel - [[VersionModel]] Version for version research unit extraction
    * @return [[ActorRef]] reference to the ew actor
    */
  def createVersionResearchUnitExtractor(versionModel: VersionModel, staticAnalysisSource: FileSource): ActorRef = {
    val r = childFactoryFun(versionModel, staticAnalysisSource, self)(context)
    context watch r
    r
  }

  /**
    * Waiting for responses for a [[VersionResearchExtraction]] task
    *
    * @param workersMap    - Actual worker map - to keep track of terminated actors and enabling some routing.
    * @param doneList      - List of [[VersionResearchUnit]] already extracted.
    * @param remainingList - List of version names waiting for extraction.
    * @return An actor receive matcher
    */
  def waitingForVersionExtractionResponses(workersMap: Map[String, ActorRef], doneList: List[VersionResearchUnit],
                                           remainingList: List[VersionModel]): Receive = {
    // Case an actor was terminated - Create a new one and send him a task
    case Terminated(actor) =>
      val invertedMap: Map[ActorRef, String] = workersMap.map(_.swap)
      val versionName = invertedMap(actor)
      val newWorkersMap = workersMap - versionName
      log.debug(s"Actor terminated versionName:$versionName")

      if (remainingList.isEmpty) {
        checkForVersionCompletion(newWorkersMap, doneList, remainingList)
      } else {
        val nextVersion = remainingList.head
        log.debug(s"new request ${nextVersion.versionName}")

        val pathToStaticAnalysis = s"$pathToProjects/${nextVersion.versionName}"
        lazy val staticAnalysisSource = sourceApi.getSource(endpointAnalyzer.pathToStaticAnalysisBuilder(pathToStaticAnalysis)).get

        val newActor = createVersionResearchUnitExtractor(nextVersion, staticAnalysisSource)
        context.become(waitingForVersionExtractionResponses(newWorkersMap + (nextVersion.versionName -> newActor),
          doneList, remainingList.tail))
      }

    // Case a VersionResearchUnit - put it on the doneList - Send another task to the actor
    case rUnit@VersionResearchUnit(versionName, _, _, _, _, _, _, _, _, _) =>
      log.debug(s"Received VersionResearchUnit versionName:$versionName")
      val newDoneList = doneList :+ rUnit
      val newWorkersMap = workersMap - versionName
      val actor = workersMap(versionName)
      context.unwatch(actor)
      actor ! PoisonPill

      if (remainingList.isEmpty) {
        checkForVersionCompletion(newWorkersMap, newDoneList, remainingList)
      } else {
        val nextVersion = remainingList.head
        log.debug(s"new request ${nextVersion.versionName}")

        val pathToStaticAnalysis = s"$pathToProjects/${nextVersion.versionName}"
        lazy val staticAnalysisSource = sourceApi.getSource(endpointAnalyzer.pathToStaticAnalysisBuilder(pathToStaticAnalysis)).get

        val newActor = createVersionResearchUnitExtractor(nextVersion, staticAnalysisSource)
        context.become(waitingForVersionExtractionResponses(newWorkersMap + (nextVersion.versionName -> newActor), newDoneList, remainingList.tail))
      }

    case GetCurrentWorkerMap() => sender() ! workersMap
    case GetDoneList() => sender() ! doneList
    case GetRemainingList() => sender() ! remainingList

    // Case I don't know - stash it!
    case r@_ =>
      log.debug(s"Stashing: $r")
      stash()
  }

  /**
    * Check if the tasks for [[VersionResearchUnit]] extraction are completed.
    *
    * @param workersMap    - Actual worker map - to keep track of terminated actors and enable some routing.
    * @param doneList      - List of [[VersionResearchUnit]] already extracted.
    * @param remainingList - List of version names waiting for extraction.
    */
  def checkForVersionCompletion(workersMap: Map[String, ActorRef], doneList: List[VersionResearchUnit], remainingList: List[VersionModel]): Unit = {
    if (workersMap.isEmpty) {
      log.debug(s"Completed, exporting to CSV")

      val versionExportPath = "./target/versionUnits.csv"
      sourceApi.exportToCsv(doneList, versionExportPath)
      log.debug(s"Exported with success to path:$versionExportPath")

      unstashAll()
      context.stop(self)
    } else {
      context.become(waitingForVersionExtractionResponses(workersMap, doneList, remainingList))
    }
  }
}
