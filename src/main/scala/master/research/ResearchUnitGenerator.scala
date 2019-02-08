package master.research

import java.io.File

import akka.actor.ActorRef
import akka.event.{LoggingAdapter, NoLogging}
import com.github.tototoshi.csv.CSVWriter
import master.mongodb.MongoUtil
import master.mongodb.elastic.ElasticVersionAnalysis
import master.mongodb.elastic.ElasticVersionAnalysisPersistence.LoadElasticVersionAnalysis

object ResearchUnitGenerator {

  implicit val log: LoggingAdapter = NoLogging

  def createElasticResearchUnit(mongoManager : ActorRef, projectId: String ): Unit = {
    def toResearchUnits(analysis: ElasticVersionAnalysis) = analysis.endpointUsageList map {
      (endpoint) => List(analysis.projectId, analysis.versionName, endpoint.verb + " - " + endpoint.endpoint, endpoint.usage.getOrElse(0))
    }
    mongoManager ! LoadElasticVersionAnalysis(projectId, observer = MongoUtil.aggregatorObserver[ElasticVersionAnalysis]( (analysisList) => {
      val researchUnits = analysisList flatMap toResearchUnits
      val f = new File("out.csv")
      val writer = CSVWriter.open(f)
      val headers = List("projectId", "versionName", "endpoint", "usage")
      writer.writeRow(headers)
      researchUnits foreach writer.writeRow
    }))
  }
}
