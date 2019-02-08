package master.research.report

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingAdapter
import master.endpoint.Endpoint
import master.mongodb.MongoUtil
import master.mongodb.coverage.CoverageModel
import master.mongodb.coverage.CoveragePersistence.LoadCoverage


object EndpointCoverageExtractor {

  case class EndpointCoverage(versionName: String, endpointCoverageMap: Map[String, Double])

  def props(projectId: String, versionName: String, endpointList: List[Endpoint],
            mongoManager: ActorRef, reportGenerator: ActorRef): Props = {
    Props(new EndpointCoverageExtractor(projectId, versionName, endpointList, mongoManager, reportGenerator))
  }
}

class EndpointCoverageExtractor(projectId: String, versionName: String, endpointList: List[Endpoint],
                                mongoManager: ActorRef, reportGenerator: ActorRef)
  extends Actor with ActorLogging {

  import EndpointCoverageExtractor._

  implicit def implicitLog: LoggingAdapter = log

  override def receive: Receive = waitingForCoverage

  override def preStart: Unit = {
    log.debug(s"Endpoint Coverage Extractor started for $versionName")
    mongoManager ! LoadCoverage(projectId, identifierOption = Some(versionName), observer =
      MongoUtil.sendBackObserver[CoverageModel](self))
  }

  def waitingForCoverage: Receive = {
    case coverageList@List(_: CoverageModel, _*) =>
      val coverageModel = coverageList.head.asInstanceOf[CoverageModel]
      val endpointCoverageMap = endpointList.map(endpoint => {
        val impactedMethodSignatures = endpoint.impactedMethodsListOption.get.map(appMethod => appMethod.signature)
        val instructionCounter = coverageModel.getInstrCounterOfMethods(impactedMethodSignatures)
        s"${endpoint.endpoint}-${endpoint.verb}" -> instructionCounter.coveredRatio
      }).toMap

      reportGenerator ! EndpointCoverage(versionName, endpointCoverageMap)
      context.stop(self)
  }
}
