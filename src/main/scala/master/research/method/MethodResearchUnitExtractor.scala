package master.research.method

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, Kill, PoisonPill, Props, Timers}
import akka.event.LoggingAdapter
import master.endpoint.ImpactedMethodsCalculator
import master.mongodb.MongoUtil
import master.mongodb.coverage.CoverageModel
import master.mongodb.coverage.CoveragePersistence.LoadCoverage
import master.mongodb.elastic.ElasticVersionAnalysis
import master.mongodb.elastic.ElasticVersionAnalysisPersistence.LoadElasticVersionAnalysis
import master.mongodb.version.VersionModel
import master.research._
import master.research.method.BugExtractor.MethodToBugAnalysis
import master.util.FileSource

import scala.concurrent.duration._
import scala.language.postfixOps

object MethodResearchUnitExtractor {
  case class MethodResearchUnitRequest(versionModel: VersionModel, staticAnalysisPath: String)
  def props(projectId: String,
            versionModel: VersionModel,
            staticAnalysisSource: FileSource,
            mongoManager: ActorRef,
            gitlabManager: ActorRef,
            researchManager: ActorRef) : Props = {
    Props(new MethodResearchUnitExtractor(projectId, versionModel, staticAnalysisSource, mongoManager, gitlabManager, researchManager))
  }
}

case class MethodResearchUnitExtractor(projectId: String, versionModel: VersionModel, staticAnalysisSource: FileSource,
                                       mongoManager: ActorRef, gitlabManager: ActorRef, researchManager: ActorRef)
  extends Actor with ActorLogging  {
  import context.dispatcher
  implicit val implicitLog: LoggingAdapter = log

  def source = staticAnalysisSource

  val timeout = context.system.scheduler.scheduleOnce(2 minutes, self, PoisonPill)

  override def preStart(): Unit = {
    log.debug(s"Starting MethodResearchUnitExtractor")
    val versionName = versionModel.versionName
    log.debug(s"Loading ElasticVersionAnalysis for version:$versionName")
    mongoManager ! LoadElasticVersionAnalysis(projectId, Some(versionName), MongoUtil.sendBackObserver[ElasticVersionAnalysis](self))
  }

  override def postStop(): Unit = {
    timeout.cancel()
    log.debug(s"MethodResearchUnitExtractor for versionName:${versionModel.versionName} stopping.")
  }

  override def receive = waitingForElasticVersionAnalysis

  /**
    * Mailbox function that waits for the elastic version analysis load from the database.
    * It then calculates the signature map and trigger the coverage load
    */
  def waitingForElasticVersionAnalysis: Receive = {
    case analysisList@List(_:ElasticVersionAnalysis, _*) =>
      val versionName = versionModel.versionName
      val elasticVersionAnalysis = analysisList.head.asInstanceOf[ElasticVersionAnalysis]
      val signatureToUsageMap: Map[String, Int] = calculateSignatureToUsageMap(elasticVersionAnalysis)
      log.debug(s"Initiating bug extractor for version:$versionName")

      val identifierList = versionModel.idCommitList
      context.actorOf(BugExtractor.props(projectId, identifierList, self, mongoManager, gitlabManager), s"BugExtractor-$versionName")

      context.become(waitingForBugQtyAnalysis(elasticVersionAnalysis, signatureToUsageMap))
    case list: List[_] if list.isEmpty => log.error(s"No ElasticVersionAnalysis found for ${versionModel.versionName}"); context.stop(self)
  }

  /**
    * Calculate signature do usage map using the [[ImpactedMethodsCalculator]] and [[MethodGraph]] classes.
    * @param elasticVersionAnalysis [[ElasticVersionAnalysis]] Object containing endpoint usage of a version.
    * @return
    */
  def calculateSignatureToUsageMap(elasticVersionAnalysis: ElasticVersionAnalysis): Map[String, Int] = {
    val versionName = versionModel.versionName
    log.debug(s"Creating generator for version:$versionName")
    def impactedMethodsCalculator: ImpactedMethodsCalculator =
      ImpactedMethodsCalculator(staticAnalysisSource)
    def generator = TGFGenerator(elasticVersionAnalysis, impactedMethodsCalculator)

    log.debug(s"Spanning the graph for version:$versionName")
    val methodGraph: MethodGraph = generator.spanGraph()

    log.debug(s"calculating signature to usage map for version:$versionName")
    methodGraph.signatureToUsageMap
  }

  /**
    * WMailbox function that waits for the [[BugExtractor]] created in the previous step to send its data back.
    * @param elasticVersionAnalysis [[ElasticVersionAnalysis]] loaded in the previous step of this actor flow.
    * @param signatureToUsageMap [[Map]] loaded in the previous step of this actor flow.
    */
  def waitingForBugQtyAnalysis(elasticVersionAnalysis: ElasticVersionAnalysis, signatureToUsageMap: Map[String, Int]): Receive = {
    case MethodToBugAnalysis(signatureToBugMap) =>
      log.debug("Signature to bug received")
      val versionName = versionModel.versionName
      mongoManager ! LoadCoverage(projectId, identifierOption = Some(versionName), observer = MongoUtil.sendBackObserver[CoverageModel](self))

      val signatureToUsageAndBugQtyMap = signatureToUsageMap map {(kv) =>
        val signature = kv._1
        val usage = kv._2
        val bugQty = signatureToBugMap.getOrElse(signature, List.empty).size

        signature -> (usage, bugQty)
      }
      log.debug("Loading elasticVersionAnalysis")
      context.become(waitingForCoverage(elasticVersionAnalysis, signatureToUsageAndBugQtyMap))
    case list: List[_] if list.isEmpty => log.error(s"No CommitImpact found for ${versionModel.versionName}"); context.stop(self)
  }

  /**
    * Mapper for a signature to usage => Maps to a [[Option]][[MethodResearchUnit]].
    * Needs oldCoverage and newCoverage models to find coverage values fo the method. If coverage value not found [[None]] is returned.
    *
    * @param oldCoverage Old coverage model with no value transformation.
    * @param kv Key Value par of the usage map
    * @return Option[[MethodResearchUnit]]
    */
  def toMethodResearchUnitOption(oldCoverage: CoverageModel)(kv: (String, (Int, Int))): Option[MethodResearchUnit] = {
    // Method Signature
    val signature = kv._1
    // Version Name
    val versionName = versionModel.versionName
    // Bug count on the method
    val bugQty = kv._2._2
    // Total usage of this method
    val totalUsage = kv._2._1
    // Total time spent in the operational environment
    val totalTimeSpent = versionModel.fromDate.getTime - versionModel.toDate.getOrElse(new Date()).getTime

    val oldMethodCoverage = oldCoverage.getMethodCoverage(signature)

    val couldFindCoverageForMethod = oldMethodCoverage.isDefined
    if(!couldFindCoverageForMethod) None
    else {
      // Instruction coverage covered ratio
      val oldInstCoverage = oldMethodCoverage.get.instructionCounter.coveredRatio
      // Branch coverage covered ratio
      val oldBranchCoverage = oldMethodCoverage.get.branchCounter.coveredRatio

      Some(MethodResearchUnit(signature, versionName, bugQty, totalUsage, totalTimeSpent, oldInstCoverage,
        oldBranchCoverage))
    }
  }

  /**
    * Mailbox function that waits for the coverage load from the database.
    * @param elasticVersionAnalysis [[ElasticVersionAnalysis]] loaded in the previous step of this actor flow.
    * @param signatureToUsageAndBugQtyMap [[Map]] loaded in the previous step of this actor flow.
    */
  def waitingForCoverage(elasticVersionAnalysis: ElasticVersionAnalysis, signatureToUsageAndBugQtyMap: Map[String, (Int, Int)]): Receive = {
    case coverageList@List(_:CoverageModel) =>
      val versionName = versionModel.versionName
      val coverageModel = coverageList.head.asInstanceOf[CoverageModel]

      val oldCoverage = coverageModel

      val methodResearchUnitList = signatureToUsageAndBugQtyMap map toMethodResearchUnitOption(oldCoverage) toList

      if (methodResearchUnitList.forall(_.isEmpty)) {
        log.error(s"No method research unit found for versionName:$versionName")
        context.stop(self)
      } else {
        log.debug(s"NotFound Ratio: ${methodResearchUnitList.count(_.isEmpty)}/${methodResearchUnitList.size}")
        log.debug(s"Sending MethodResearchUnit List back of version:$versionName")
        researchManager ! methodResearchUnitList.flatten
      }

    case list: List[_] if list.isEmpty => log.error(s"No CoverageModel found for ${versionModel.versionName}"); context.stop(self)
  }
}