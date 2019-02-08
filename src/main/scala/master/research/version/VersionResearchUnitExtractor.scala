package master.research.version

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingAdapter
import master.endpoint.ImpactedMethodsCalculator
import master.mongodb.MongoUtil
import master.mongodb.coverage.CoverageModel
import master.mongodb.coverage.CoveragePersistence.LoadCoverage
import master.mongodb.elastic.ElasticVersionAnalysis
import master.mongodb.elastic.ElasticVersionAnalysisPersistence.LoadElasticVersionAnalysis
import master.mongodb.version.VersionModel
import master.research.{MethodGraph, TGFGenerator}
import master.util.{FileSource, SourceApi}

object VersionResearchUnitExtractor {
  def props(projectId: String,
            versionModel: VersionModel, staticAnalysisSource: FileSource,
            mongoManager: ActorRef, researchManager: ActorRef, sourceApi: SourceApi): Props = {
    Props( new VersionResearchUnitExtractor(projectId, versionModel, staticAnalysisSource, mongoManager,
      researchManager, sourceApi))
  }

  def calculateDistanceToAverageUsage(average: Double)(usage: Double): Double = (usage + average)/average

  def calculateDistanceToAvgWeightMap(signatureToUsageMap: Map[String, Int]): Map[String, Double] = {
    val avg: Double = signatureToUsageMap.values.sum/signatureToUsageMap.size

    // Distance to average
    signatureToUsageMap.map { (kv) =>  kv._1 -> VersionResearchUnitExtractor.calculateDistanceToAverageUsage(avg)(kv._2) }
  }

  def calculateOperationalWeightMap(signatureToUsageMap: Map[String, Int]): Map[String, Double] = {
    val pairList: List[(String, Int)] = signatureToUsageMap.toList.sortBy(kv => kv._2)

    pairList.zipWithIndex.map((kvi) =>  {
      val i = kvi._2
      val signature = kvi._1._1
      if(i < pairList.length/3) signature -> 1.0
      else if(i < pairList.length/3) signature -> 3.0
      else signature -> 9.0
    }).toMap
  }
}

case class VersionResearchUnitExtractor(projectId: String, versionModel: VersionModel, staticAnalysisSource: FileSource,
                                        mongoManager: ActorRef, researchManager: ActorRef, sourceApi: SourceApi,
                                        elasticVersionAnalysisOption: Option[ElasticVersionAnalysis] = None) extends Actor with ActorLogging {
  import VersionResearchUnitExtractor._
  implicit val implicitLog: LoggingAdapter = log
  private val versionName = versionModel.versionName

  override def preStart(): Unit = {
    log.debug("Starting VersionResearchUnitExtractor")
    log.debug(s"Loading ElasticVersionAnalysis for version:$versionName")
    elasticVersionAnalysisOption match {
      case None =>
        mongoManager ! LoadElasticVersionAnalysis(projectId, Some(versionName), MongoUtil.sendBackObserver[ElasticVersionAnalysis](self))
      case Some(elasticVersionAnalysis: ElasticVersionAnalysis) =>
        self ! elasticVersionAnalysis
    }
  }

  override def receive = waitingForElasticVersionAnalysis

  /**
    * Waits for the [[ElasticVersionAnalysis]] requested on the preStart hook.
    */
  def waitingForElasticVersionAnalysis: Receive = {
    case analysisList@List(_:ElasticVersionAnalysis, _*) =>
      val elasticVersionAnalysis = analysisList.head.asInstanceOf[ElasticVersionAnalysis]
      val signatureToUsageMap: Map[String, Int] = calculateSignatureToUsageMap(elasticVersionAnalysis)
      log.debug(s"Loading Coverage for version:$versionName")
      mongoManager ! LoadCoverage(projectId, identifierOption = Some(versionName), observer = MongoUtil.sendBackObserver[CoverageModel](self))
      context.become(waitingForCoverage(elasticVersionAnalysis, signatureToUsageMap))
    case list: List[_] if list.isEmpty => log.error(s"No ElasticVersionAnalysis found for ${versionModel.versionName}"); context.stop(self)
  }

  /**
    * Calculate signature do usage map using the [[ImpactedMethodsCalculator]] and [[MethodGraph]] classes.
    * @param elasticVersionAnalysis [[ElasticVersionAnalysis]] Object containing endpoint usage of a version.
    */
  def calculateSignatureToUsageMap(elasticVersionAnalysis: ElasticVersionAnalysis): Map[String, Int] = {
    log.debug(s"Creating generator for version:$versionName")
    def impactedMethodsCalculator: ImpactedMethodsCalculator = ImpactedMethodsCalculator(staticAnalysisSource)
    def generator = TGFGenerator(elasticVersionAnalysis, impactedMethodsCalculator)

    log.debug(s"Spanning the graph for version:$versionName")
    val methodGraph: MethodGraph = generator.spanGraph()

    log.debug(s"calculating signature to usage map for version:$versionName")
    methodGraph.signatureToUsageMap
  }

  /**
    * Waits for the coverage load triggered in the previous step and then calculate the [[VersionResearchUnit]] and send
    * back to the Research Manager.
    * @param elasticVersionAnalysis [[ElasticVersionAnalysis]] loaded in the previous step of the workflow.
    * @param signatureToUsageMap [[Map]] containing the signature -> endpoint usage mappings.
    */
  def waitingForCoverage(elasticVersionAnalysis: ElasticVersionAnalysis, signatureToUsageMap: Map[String, Int] ): Receive = {
    case coverageList@List(_:CoverageModel) =>
      val versionName = versionModel.versionName
      val coverageModel = coverageList.head.asInstanceOf[CoverageModel]
      val DAUWeightMap = calculateDistanceToAvgWeightMap(signatureToUsageMap)
      val operationalWeightMap = calculateOperationalWeightMap(signatureToUsageMap)

//      val weightMapUnitList = weightMap.map((kv) => WeightMapUnit(kv._1, kv._2))
//      sourceApi.exportToCsv(weightMapUnitList, s"./target/$versionName-weightMap.csv")

      val oldCoverage = coverageModel
      val newCoverage = coverageModel.applyWeightMap(DAUWeightMap)
      val operCoverage = coverageModel.applyWeightMap(operationalWeightMap)

      val oldInstCoverage =  oldCoverage.bundleCoverage.instructionCounter.coveredRatio
      val newInstCoverage = newCoverage.bundleCoverage.instructionCounter.coveredRatio
      val operInstCoverage = operCoverage.bundleCoverage.instructionCounter.coveredRatio

      val oldBranchCoverage =  oldCoverage.bundleCoverage.branchCounter.coveredRatio
      val newBranchCoverage = newCoverage.bundleCoverage.branchCounter.coveredRatio
      val operBranchCoverage = operCoverage.bundleCoverage.branchCounter.coveredRatio

      val totalUsage = elasticVersionAnalysis.endpointUsageList.flatMap(_.usage).sum
      val totalTimeSpent = versionModel.toDate.getOrElse(new Date()).getTime - versionModel.fromDate.getTime

      log.debug(s"Sending VersionResearchUnit back of version:$versionName")
      researchManager ! VersionResearchUnit(versionName, versionModel.bugCount, totalUsage, totalTimeSpent,
        oldInstCoverage, newInstCoverage, operInstCoverage, oldBranchCoverage, newBranchCoverage, operBranchCoverage)
    case list: List[_] if list.isEmpty => log.error(s"No CoverageModel found for ${versionModel.versionName}"); context.stop(self)
  }
}