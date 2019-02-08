package master.mongodb.elastic

import akka.event.LoggingAdapter
import master.mongodb.{MongoLoadRequest, MongoPersistence, MongoRequest}
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.UpdateOptions
import org.mongodb.scala.{MongoCollection, Observer, SingleObservable}

/**
  * Companion object for[[ElasticVersionAnalysisPersistence]]
  */
object ElasticVersionAnalysisPersistence {
  case class PersistElasticVersionAnalysis(elasticVersionAnalysis: ElasticVersionAnalysis,
                                           callback: () => Any = MongoRequest.doNothing) extends MongoRequest(callback)
  case class LoadElasticVersionAnalysis(projectId: String, versionNameOption: Option[String] = None,
                                        override val observer: Observer[ElasticVersionAnalysis],
                                        callback: () => Any = MongoRequest.doNothing) extends MongoLoadRequest(observer, callback)

  /**
    * Constructor of a [[ElasticVersionAnalysisPersistence]] instance.
    * @param endpointMapImpactCollection [[MongoCollection]] for accessing the MongoDB storing [[ElasticVersionAnalysis]]
    * @param log [[LoggingAdapter]] to log in the name of the mongo manger object
    * @return [[ElasticVersionAnalysisPersistence]] instance
    */
  def apply(endpointMapImpactCollection: MongoCollection[ElasticVersionAnalysis])
           (implicit log: LoggingAdapter): ElasticVersionAnalysisPersistence =
    new ElasticVersionAnalysisPersistence(endpointMapImpactCollection, log)
}

/**
  * Persistence auxiliary class for [[ElasticVersionAnalysis]]
  * @param endpointMapImpactCollection [[MongoCollection]] for accessing the MongoDB storing [[ElasticVersionAnalysis]]
  * @param log [[LoggingAdapter]] to log in the name of the mongo manger object
  */
class ElasticVersionAnalysisPersistence(endpointMapImpactCollection:  MongoCollection[ElasticVersionAnalysis], implicit val log: LoggingAdapter)
  extends MongoPersistence[ElasticVersionAnalysis]{
  import ElasticVersionAnalysisPersistence._

  override protected def createWriteRequest(request: MongoRequest) = {
    val PersistElasticVersionAnalysis(elasticVersionAnalysis: ElasticVersionAnalysis, _) = request
    log.debug(s"ElasticVersionAnalysis write request received for projectId:${elasticVersionAnalysis.projectId}," +
      s" identifier:${elasticVersionAnalysis.versionName}")
    endpointMapImpactCollection.replaceOne(
      and(
        equal("projectId", elasticVersionAnalysis.projectId),
        equal("versionName", elasticVersionAnalysis.versionName)
      ),
      elasticVersionAnalysis,
      UpdateOptions().upsert(true)
    ).asInstanceOf[SingleObservable[Any]]
  }

  override protected def createLoadRequest(request: MongoRequest) = {
    val LoadElasticVersionAnalysis(projectId: String, versionNameOption: Option[String], _, _) = request
    versionNameOption match {
      case Some(versionName) =>
        log.debug(s"ElasticVersionAnalysis load request received for projectId:$projectId, versionName:$versionName")
        endpointMapImpactCollection.find(
          and(
            equal("projectId", projectId),
            equal("versionName", versionName)),
        ).first()
      case None =>
        log.debug(s"ElasticVersionAnalysis load request received for projectId:$projectId")
        endpointMapImpactCollection.find(
          equal("projectId", projectId),
        )
    }
  }

  override protected def className = ElasticVersionAnalysis.toString()
}
