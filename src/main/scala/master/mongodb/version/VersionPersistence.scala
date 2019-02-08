package master.mongodb.version

import akka.event.LoggingAdapter
import com.mongodb.client.model.WriteModel
import master.mongodb.version.VersionPersistence.{LoadVersions, PersistVersions}
import master.mongodb.{MongoLoadRequest, MongoPersistence, MongoRequest}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.{BulkWriteOptions, ReplaceOneModel, UpdateOptions}
import org.mongodb.scala.{MongoCollection, Observable, Observer, SingleObservable}

/**
  * Companion object for for [[VersionPersistence]]
  */
object VersionPersistence {
  case class PersistVersions(diffs: List[VersionModel],
                             callback: () => Any = MongoRequest.doNothing) extends MongoRequest(callback)
  case class LoadVersions(projectId: String, override val observer: Observer[VersionModel],
                          versionTagOption: Option[String] = None,
                          callback: () => Any = MongoRequest.doNothing) extends MongoLoadRequest(observer, callback)

  /**
    * Constructor of a [[VersionPersistence]] instance.
    * @param versionCollection - [[MongoCollection]] for accessing the MongoDB storing [[VersionModel]]
    * @param log - LoggingAdapter to log in the name of the mongoManager object
    * @return new [[VersionPersistence]] instance.
    */
  def apply(versionCollection: MongoCollection[VersionModel])(implicit log: LoggingAdapter): VersionPersistence =
    new VersionPersistence(versionCollection, log)
}

/**
  * Persistence auxiliary class for [[VersionModel]]
  * @param versionCollection - [[MongoCollection]] for accessing the MongoDB storing [[VersionModel]]
  * @param log - LoggingAdapter to log in the name of the mongoManager object
  */
class VersionPersistence(versionCollection:  MongoCollection[VersionModel], implicit val log: LoggingAdapter)
  extends MongoPersistence[VersionModel]{

  override def createWriteRequest(request: MongoRequest) : SingleObservable[Any] = {
    val PersistVersions(versionList: List[VersionModel], _) = request
    log.debug(s"${versionList.size} versions being sent to mongoDB")

    // Creating a BulkWrite request for mongo
    val writes: List[WriteModel[_ <: VersionModel]] = for {
      version <- versionList
    } yield ReplaceOneModel(Document("versionName" -> version.versionName, "projectId" -> version.projectId),
      version, UpdateOptions().upsert(true))

    versionCollection.bulkWrite(writes, BulkWriteOptions().ordered(false)).asInstanceOf[SingleObservable[Any]]
  }

  /**
    * Check the versionTagOption parameter, if present create a load request which takes it into account,
    * if not present just uses the projectId as a BSON filter.
    * @param request [[LoadVersions]] request
    * @return [[Observable[VersionModel]]
    */
  override def createLoadRequest(request: MongoRequest): Observable[VersionModel] = {
    val LoadVersions(projectId: String, _, versionTagOption, _) = request
    versionTagOption match {
      case Some(versionTag) =>
        log.debug(s"Version load request received for projectId:$projectId and versionName:$versionTag")
        versionCollection.find(
          and(
            equal("projectId", projectId),
            equal("versionName", versionTag)
          )
        )
      case None =>
        log.debug(s"Version load request received for projectId:$projectId")
        versionCollection.find(
          equal("projectId", projectId)
        )
    }
  }

  override protected def className: String = VersionModel.toString
}
