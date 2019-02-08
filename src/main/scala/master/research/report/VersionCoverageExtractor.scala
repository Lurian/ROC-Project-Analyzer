package master.research.report

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingAdapter
import master.endpoint.ImpactedMethodsCalculator
import master.mongodb.MongoUtil
import master.mongodb.coverage.CoverageModel
import master.mongodb.coverage.CoveragePersistence.LoadCoverage
import master.mongodb.elastic.ElasticVersionAnalysis
import master.mongodb.version.VersionImpact
import master.mongodb.version.VersionImpactPersistence.LoadVersionImpact
import master.research.report.VersionCoverageExtractor.VersionCoverage
import master.research.version.VersionResearchUnitExtractor
import master.research.{MethodGraph, TGFGenerator}
import master.util.FileSource


object VersionCoverageExtractor {

  case class VersionCoverage(versionName: String, classicCoveredRatio: Double, operationalCoveredRatio: Double,
                             proposedCoveredRatio: Double)


  def props(projectId: String, versionName: String, elasticVersionAnalysis: ElasticVersionAnalysis,
            staticAnalysisSource: FileSource, mongoManager: ActorRef, reportGenerator: ActorRef): Props = {
    Props(new VersionCoverageExtractor(projectId, versionName, elasticVersionAnalysis, staticAnalysisSource,
      mongoManager, reportGenerator))
  }
}

class VersionCoverageExtractor(projectId: String, versionName: String, elasticVersionAnalysis: ElasticVersionAnalysis,
                               staticAnalysisSource: FileSource, mongoManager: ActorRef, reportGenerator: ActorRef)
  extends Actor with ActorLogging {
  import VersionResearchUnitExtractor._

  implicit def implicitLog: LoggingAdapter = log

  override def receive: Receive = waitingForVersionImpact

  override def preStart: Unit = {
    log.debug(s"Endpoint Coverage Extractor started for $versionName")
    mongoManager ! LoadVersionImpact(projectId, tagOption = Some(versionName), observer =
      MongoUtil.sendBackObserver[VersionImpact](self))
  }

  def waitingForVersionImpact: Receive = {
    case versionImpactList@List(_: VersionImpact, _*) =>
      val versionImpactModel = versionImpactList.head.asInstanceOf[VersionImpact]

      val signatures: List[String] = versionImpactModel.impactList.filter(_.fileName.startsWith("src/main/java/")).flatMap(impact => {
        val className = impact.fileName.substring(14).reverse.substring(5).reverse
        val classSignature = className.replace("/", ".")
        impact.methods.map(methodName => s"$classSignature.$methodName")
      })
      log.debug(signatures.toString())
      mongoManager ! LoadCoverage(projectId, identifierOption = Some(versionName), observer =
        MongoUtil.sendBackObserver[CoverageModel](self))
      context.become(waitingForCoverage(signatures))
  }

  def waitingForCoverage(signatures: List[String]): Receive = {
    case coverageList@List(_: CoverageModel, _*) =>
      val signatureToUsageMap = calculateSignatureToUsageMap(elasticVersionAnalysis)

      val DAUWeightMap = calculateDistanceToAvgWeightMap(signatureToUsageMap)
      val operationalWeightMap = calculateOperationalWeightMap(signatureToUsageMap)

      val classicCoverageModel = coverageList.head.asInstanceOf[CoverageModel]
      val newCoverageModel = classicCoverageModel.applyWeightMap(DAUWeightMap)
      val operCoverageModel = classicCoverageModel.applyWeightMap(operationalWeightMap)

      val classicalInstructionCounter = classicCoverageModel.getInstrCounterOfMethods(signatures)
      val operationalInstructionCounter = operCoverageModel.getInstrCounterOfMethods(signatures)
      val proposedInstructionCounter = newCoverageModel.getInstrCounterOfMethods(signatures)

      reportGenerator ! VersionCoverage(versionName, classicalInstructionCounter.coveredRatio,
        operationalInstructionCounter.coveredRatio, proposedInstructionCounter.coveredRatio)
      context.stop(self)
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
}
