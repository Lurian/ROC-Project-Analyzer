package master.mongodb.endpoint

import akka.event.LoggingAdapter
import master.mongodb.{MongoLoadRequest, MongoPersistence, MongoRequest}
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.UpdateOptions
import org.mongodb.scala.{MongoCollection, Observer, SingleObservable}

/**
  * Companion object for for [[EndpointImpactMapPersistence]]
  */
object EndpointImpactMapPersistence {
  case class PersistEndpointImpactMap(endpointImpactMap: EndpointImpactModel,
                                      callback: () => Any = MongoRequest.doNothing) extends MongoRequest(callback)
  case class LoadEndpointImpactMap(projectId: String, versionName: Option[String] = None, override val observer: Observer[EndpointImpactModel],
                                   callback: () => Any = MongoRequest.doNothing) extends MongoLoadRequest(observer, callback)

  /**
    * Constructor of a [[EndpointImpactMapPersistence]] instance.
    * @param endpointMapImpactCollection - [[MongoCollection]] for accessing the MongoDB storing [[EndpointImpactModel]]
    * @param log - LoggingAdapter to log in the name of the mongoManager object
    * @return new [[EndpointImpactMapPersistence]] instance.
    */
  def apply(endpointMapImpactCollection: MongoCollection[EndpointImpactModel])(implicit log: LoggingAdapter): EndpointImpactMapPersistence =
    new EndpointImpactMapPersistence(endpointMapImpactCollection, log)
}

/**
  * Persistence auxiliary class for [[EndpointImpactModel]]
  * @param endpointMapImpactCollection - [[MongoCollection]] for accessing the MongoDB storing [[EndpointImpactModel]]
  * @param log - LoggingAdapter to log in the name of the mongoManager object
  */
class EndpointImpactMapPersistence(endpointMapImpactCollection:  MongoCollection[EndpointImpactModel], implicit val log: LoggingAdapter)
  extends MongoPersistence[EndpointImpactModel] {
  import EndpointImpactMapPersistence._

  override protected def createWriteRequest(request: MongoRequest) = {
    val PersistEndpointImpactMap(endpointImpactMap: EndpointImpactModel, _) = request
    log.debug(s"EndpointMapImpact write request received for projectId:${endpointImpactMap.projectId}," +
      s" identifier:${endpointImpactMap.versionName}")
    endpointMapImpactCollection.replaceOne(
      and(
        equal("projectId", endpointImpactMap.projectId),
        equal("versionName", endpointImpactMap.versionName)
      ),
      endpointImpactMap,
      UpdateOptions().upsert(true)
    ).asInstanceOf[SingleObservable[Any]]
  }

  override protected def createLoadRequest(request: MongoRequest) = {
    val LoadEndpointImpactMap(projectId: String, versionNameOption: Option[String], observer: Observer[EndpointImpactModel], _) = request
    versionNameOption match {
      case Some(versionName) =>
        log.debug(s"EndpointImpactMap load request received for projectId:$projectId, versionName:$versionName")
        endpointMapImpactCollection.find(
          and(
            equal("projectId", projectId),
            equal("versionName", versionName)),
        ).first()
      case None =>
        log.debug(s"EndpointImpactMap load request received for projectId:$projectId")
        endpointMapImpactCollection.find(
            equal("projectId", projectId)
        )
    }
  }

  override protected def className = EndpointImpactModel.toString()
}
