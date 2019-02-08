package master.research.method

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, OneForOneStrategy, PoisonPill, Props, Stash, SupervisorStrategy, Terminated}
import master.endpoint.EndpointAnalyzer
import master.mongodb.version.VersionModel
import master.project.task.ResearchUnitManager
import master.project.task.ResearchUnitManager.{GetCurrentWorkerMap, GetDoneList, GetRemainingList, MethodResearchExtraction}
import master.research.version.VersionResearchUnit
import master.util.{FileSource, SourceApi}


object MethodUnitManager {

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
    Props(new MethodUnitManager(projectId, pathToProjects, filteredVersionList, childFactoryFun, numberOfWorkers,
      sourceApi, mongoManager, gitlabManager, researchManager, EndpointAnalyzer()))
  }

  /**
    * Creates a [[ActorRefFactory]] function for a [[MethodResearchUnitExtractor]]
    *
    * @param projectId    [[String]] Id of the project being analyzed.
    * @param mongoManager [[ActorRef]] of the persistence manager.
    * @return
    */
  def methodResearchUnitExtractorChildFactory(projectId: String, mongoManager: ActorRef, gitlabManager: ActorRef):
  (VersionModel, FileSource, ActorRef) ⇒ ActorRefFactory ⇒ ActorRef = {
    val childPropsFun = (versionModel: VersionModel, staticAnalysisSource: FileSource, parent: ActorRef) =>
      (f: ActorRefFactory) => f.actorOf(MethodResearchUnitExtractor.props(projectId, versionModel, staticAnalysisSource, mongoManager, gitlabManager, parent))
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
  * @param researchManager     [[ActorRef]] manager of the actor being created
  * @param endpointAnalyzer [[EndpointAnalyzer]] auxiliary class to help on getting the path to the static analysis.
  */
class MethodUnitManager(projectId: String, pathToProjects: String, filteredVersionList: List[VersionModel],
                        childFactoryFun: (VersionModel, FileSource, ActorRef) => ActorRefFactory => ActorRef,
                        numberOfWorkers: Int, sourceApi: SourceApi, mongoManager: ActorRef,
                        gitlabManager: ActorRef, researchManager: ActorRef, endpointAnalyzer: EndpointAnalyzer) extends Actor with ActorLogging with Stash {
  override def preStart(): Unit = {
    log.debug(s"Starting MethodUnitManager with projectId:$projectId")
    triggerInitialMethodExtraction()
  }

  override val supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy() { case _: Exception => Stop }

  override def receive: Receive = {
    case _ => log.error("Message received before pre-start")
  }

  /**
    * Mapper from [[VersionModel]] to Signature -> [[ActorRef]] of a [[MethodResearchUnitExtractor]].
    *
    * @param versionModel [[VersionModel]] to be mapped.
    * @return Signature -> [[ActorRef]]
    */
  def toSignatureAndMethodWorker(versionModel: VersionModel): (String, ActorRef) = {
    val pathToStaticAnalysis = s"$pathToProjects/${versionModel.versionName}"
    lazy val staticAnalysisSource = sourceApi.getSource(endpointAnalyzer.pathToStaticAnalysisBuilder(pathToStaticAnalysis)).get
    val r = createMethodResearchUnitExtractor(versionModel, staticAnalysisSource)
    versionModel.versionName -> r
  }

  /**
    * Create a new actor for method research unit extraction. Automatically send it a request for extraction.
    *
    * @param versionModel [[VersionModel]] Version for version research unit extraction
    * @return [[ActorRef]] reference to the ew actor
    */
  def createMethodResearchUnitExtractor(versionModel: VersionModel, staticAnalysisSource: FileSource): ActorRef = {
    val r = childFactoryFun(versionModel, staticAnalysisSource, self)(context)
    context watch r
    r
  }

  /**
    * Trigger the initial Method Extraction using the default number of workers to concurrent extract data.
    */
  def triggerInitialMethodExtraction(): Unit = {
    val initialVersions = filteredVersionList.take(numberOfWorkers)
    val workersMap: Map[String, ActorRef] = initialVersions map toSignatureAndMethodWorker toMap

    context.become(waitingForMethodExtractionResponses(workersMap, List.empty, filteredVersionList.drop(numberOfWorkers)))
  }

  /**
    * Waiting for responses for a [[MethodResearchExtraction]] task
    *
    * @param workersMap    - Actual worker map - to keep track of terminated actors and enabling some routing.
    * @param doneList      - List of [[VersionResearchUnit]] already extracted.
    * @param remainingList - List of version names waiting for extraction.
    * @return An actor receive matcher
    */
  def waitingForMethodExtractionResponses(workersMap: Map[String, ActorRef], doneList: List[MethodResearchUnit], remainingList: List[VersionModel]): Receive = {
    // Case an actor was terminated - Create a new one and send him a task
    case Terminated(actor) =>
      val invertedMap: Map[ActorRef, String] = workersMap.map(_.swap)
      val versionName = invertedMap(actor)
      val newWorkersMap = workersMap - versionName
      log.debug(s"$versionName actor terminated")

      if (remainingList.isEmpty) {
        checkForMethodCompletion(newWorkersMap, doneList, remainingList)
      } else {
        val nextVersion = remainingList.head
        log.debug(s"new request ${remainingList.head.versionName}")

        val pathToStaticAnalysis = s"$pathToProjects/${nextVersion.versionName}"
        lazy val staticAnalysisSource = sourceApi.getSource(endpointAnalyzer.pathToStaticAnalysisBuilder(pathToStaticAnalysis)).get

        val newActor = createMethodResearchUnitExtractor(nextVersion, staticAnalysisSource)
        context.become(waitingForMethodExtractionResponses(newWorkersMap + (nextVersion.versionName -> newActor), doneList, remainingList.tail))
      }

    // Case a MethodResearchUnit - put it on the doneList - Send another task to the actor
    case rUnitList: List[_] =>
      val newDoneList: List[MethodResearchUnit] = doneList ++ rUnitList.asInstanceOf[List[MethodResearchUnit]]
      val versionName = rUnitList.head.asInstanceOf[MethodResearchUnit].versionName
      val newWorkersMap = workersMap - versionName
      log.debug(s"$versionName actor returned - ${workersMap.keys.toString()}")
      val actor = workersMap(versionName)
      context.unwatch(actor)
      actor ! PoisonPill

      if (remainingList.isEmpty) {
        checkForMethodCompletion(newWorkersMap, newDoneList, remainingList)
      } else {
        val nextVersion = remainingList.head
        log.debug(s"new request ${nextVersion.versionName}")

        val pathToStaticAnalysis = s"$pathToProjects/${nextVersion.versionName}"
        lazy val staticAnalysisSource = sourceApi.getSource(endpointAnalyzer.pathToStaticAnalysisBuilder(pathToStaticAnalysis)).get

        val newActor = createMethodResearchUnitExtractor(nextVersion, staticAnalysisSource)
        context.become(waitingForMethodExtractionResponses(newWorkersMap + (nextVersion.versionName -> newActor), newDoneList, remainingList.tail))
      }

    case GetCurrentWorkerMap() => sender() ! workersMap
    case GetDoneList() => sender() ! doneList
    case GetRemainingList() => sender() ! remainingList

    // Case I don't know - stash it!
    case r@_ =>
      log.debug(s"MethodExtraction Stashing: $r")
      stash()
  }

  /**
    * Check if the tasks for [[VersionResearchUnit]] extraction are completed.
    *
    * @param workersMap    - Actual worker map - to keep track of terminated actors and enable some routing.
    * @param doneList      - List of [[VersionResearchUnit]] already extracted.
    * @param remainingList - List of version names waiting for extraction.
    */
  def checkForMethodCompletion(workersMap: Map[String, ActorRef], doneList: List[MethodResearchUnit], remainingList: List[VersionModel]): Unit = {
    if (workersMap.isEmpty) {
      log.debug(s"Completed, exporting to CSV")
      val methodExportPath = "./target/methodUnits.csv"
      sourceApi.exportToCsv(doneList, methodExportPath)
      unstashAll()
      context.stop(self)
    } else {
      context.become(waitingForMethodExtractionResponses(workersMap, doneList, remainingList))
    }
  }
}
