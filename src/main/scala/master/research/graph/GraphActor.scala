package master.research.graph

import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props, Stash}
import akka.event.LoggingAdapter
import master.endpoint.{EndpointAnalyzer, ImpactedMethodsCalculator}
import master.mongodb.MongoUtil
import master.mongodb.elastic.ElasticVersionAnalysis
import master.mongodb.elastic.ElasticVersionAnalysisPersistence.LoadElasticVersionAnalysis
import master.mongodb.version.VersionModel
import master.mongodb.version.VersionPersistence.LoadVersions
import master.research.graph.GraphActor.GraphRequest
import master.research.graph.GraphType.GraphType
import master.research.method.BugExtractor
import master.research.method.BugExtractor.MethodToBugAnalysis
import master.research.{MethodGraph, TGFGenerator}
import master.util.SourceApi


/**
  * Companion object for [[GraphActor]]
  */
object GraphActor {
  case class GraphRequest(versionName: String, projectPath: String, graphType: GraphType, maxRootMethod: Int, maxHeight: Int)

  /**
    * Props creation for [[GraphActor]]
    * @param projectId [[String]] project identification.
    * @param BEFactory [[Function]] factory function for BugExtraction actor.
    * @param requester [[ActorRef]]
    * @param mongoManager  [[ActorRef]] mongo persistence actor reference.
    * @param gitlabManager [[ActorRef]] gitlab service actor reference.
    * @return
    */
  def props(projectId: String, requester: ActorRef,
            BEFactory: (List[String], ActorRef) ⇒ ActorRefFactory ⇒ ActorRef,
            mongoManager: ActorRef, gitlabManager: ActorRef, sourceApi: SourceApi): Props =
    Props(new GraphActor(projectId, requester, BEFactory, mongoManager, gitlabManager, sourceApi, EndpointAnalyzer()))

  /**
    * Creates a [[ActorRefFactory]] function for a [[BugExtractor]]
    *
    * @param projectId     [[String]] Id of the project being analyzed.
    * @param mongoManager  [[ActorRef]] of the persistence manager.
    * @param gitlabManager [[ActorRef]] of the gitlab manager.
    * @return
    */
  def bugExtractorFactory(projectId: String,  mongoManager: ActorRef, gitlabManager: ActorRef):
  (List[String], ActorRef) ⇒ ActorRefFactory ⇒ ActorRef = {
    val childPropsFun = (identifierList: List[String], requester: ActorRef) =>
      (f: ActorRefFactory) => {
        f.actorOf(BugExtractor.props(projectId, identifierList, requester, mongoManager, gitlabManager),
          s"BugExtractor-$projectId-${System.currentTimeMillis()}")
      }
    childPropsFun
  }
}

/**
  * Actor that receive requests to span graphs in different formats and saves them on the target folder.
  *
  * @param projectId     [[String]] project identification.
  * @param requester     [[ActorRef]]
  * @param mongoManager  [[ActorRef]] mongo persistence actor reference.
  * @param gitlabManager [[ActorRef]] gitlab service actor reference.
  * @param endpointAnalyzer [[EndpointAnalyzer]] auxiliary class to help on getting the path to the static analysis.
  */
class GraphActor(projectId: String, requester: ActorRef,
                 BEFactory: (List[String], ActorRef) ⇒ ActorRefFactory ⇒ ActorRef,
                 mongoManager: ActorRef, gitlabManager: ActorRef, sourceApi: SourceApi, endpointAnalyzer: EndpointAnalyzer)
  extends Actor with ActorLogging with Stash{
  implicit val implicitLog: LoggingAdapter = log

  override def preStart(): Unit = {
    log.debug(s"Starting GraphActor for projectId:$projectId")
  }

  override def receive: Receive = waitForRequest

  def waitForRequest: Receive = {
    case GraphRequest(versionName, projectPath, graphType, maxRootMethod, maxHeight) =>
      log.debug(s"GraphRequest received for graphType:$graphType")
      val pathToStaticAnalysis = endpointAnalyzer.pathToStaticAnalysisBuilder(projectPath)
      val versionImpactedMethodsCalculator = ImpactedMethodsCalculator(sourceApi.getSource(pathToStaticAnalysis).get)
      mongoManager ! LoadElasticVersionAnalysis(projectId, versionNameOption = Some(versionName),
        observer = MongoUtil.sendBackObserver[ElasticVersionAnalysis](self))
      context.become(waitForElasticVersionAnalysis(versionName, versionImpactedMethodsCalculator, graphType, maxRootMethod, maxHeight))
  }

  def waitForElasticVersionAnalysis(versionName: String, calculator: ImpactedMethodsCalculator, graphType: GraphType,
                                    maxRootMethod: Int, maxHeight: Int): Receive = {
    case list: List[_] =>
      val elasticList = list.asInstanceOf[List[ElasticVersionAnalysis]]
      val analysis = elasticList.head
      val generator = TGFGenerator(analysis,calculator)
      val graph = generator.spanGraph(maxRootMethod, maxHeight)
      graphType match {
       case GraphType.TGF =>
         generator.createTFG(graph, s"$versionName-graph-$maxRootMethod-$maxHeight.tgf", false)
         unstashAll()
         context.become(waitForRequest)
       case GraphType.GML =>
         mongoManager ! LoadVersions(projectId, versionTagOption = Some(versionName),
           observer = MongoUtil.sendBackObserver[VersionModel](self))
          context.become(waitForVersionLoad(graph, generator, maxRootMethod, maxHeight))
    }
    case _ => stash()
  }

  def waitForVersionLoad(graph: MethodGraph, generator: TGFGenerator, maxRootMethod: Int, maxHeight: Int): Receive = {
    case list: List[_] =>
      val versionList = list.asInstanceOf[List[VersionModel]]
      val versionModel = versionList.head
      val identifierList = versionModel.idCommitList
      BEFactory(identifierList, self)(context)
      context.become(waitForBugExtraction(graph, versionModel.versionName, generator, maxRootMethod, maxHeight))
    case _ => stash()
  }

  def waitForBugExtraction(graph: MethodGraph, versionName: String, generator: TGFGenerator,
                           maxRootMethod: Int, maxHeight: Int): Receive = {
    case methodToBugAnalysis: MethodToBugAnalysis =>
      val newGraph = graph.addMethodToBugAnalysis(methodToBugAnalysis).spreadBugImpact
      generator.createGML(newGraph, s"$versionName-graph-$maxRootMethod-$maxHeight.graphml", false)
      generator.createGML(newGraph.onlyRootNodes, s"$versionName-graph-endpoints.graphml", false)
      unstashAll()
      context.become(waitForRequest)
    case _ => stash()
  }
}
