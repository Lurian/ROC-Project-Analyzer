package master.mongodb.diff

import akka.event.LoggingAdapter
import com.mongodb.client.model.WriteModel
import master.mongodb.diff.DiffPersistence.{LoadDiff, LoadDiffsIn, PersistDiffs}
import master.mongodb.{MongoLoadRequest, MongoPersistence, MongoRequest}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.{and, equal, in}
import org.mongodb.scala.model.{BulkWriteOptions, ReplaceOneModel, UpdateOptions}
import org.mongodb.scala.{MongoCollection, Observer, SingleObservable}

/**
  * Companion object for [[DiffPersistence]]
  */
object DiffPersistence {
  case class PersistDiffs(diffs: List[DiffModel],
                          callback: () => Any = MongoRequest.doNothing) extends MongoRequest(callback)
  case class LoadDiff(projectId: String, override val observer: Observer[DiffModel], identifierOption: Option[String] = None,
                      callback: () => Any = MongoRequest.doNothing) extends MongoLoadRequest(observer, callback)
  case class LoadDiffsIn(projectId: String, identifierList: List[String], override val observer: Observer[DiffModel],
                         callback: () => Any = MongoRequest.doNothing) extends MongoLoadRequest(observer, callback)

  /**
    * Constructor of a [[DiffPersistence]] instance.
    * @param diffCollection [[MongoCollection]] for accessing the MongoDB storing [[DiffModel]]
    * @param log [[LoggingAdapter]] to log in the name of the mongo manger object
    * @return A [[DiffPersistence]] instance
    */
  def apply(diffCollection: MongoCollection[DiffModel])(implicit log: LoggingAdapter): DiffPersistence =
    new DiffPersistence(diffCollection, log)
}

/**
  * Persistence auxiliary class for [[DiffModel]]
  * @param diffCollection [[MongoCollection]] for accessing the MongoDB storing [[DiffModel]]
  * @param log [[LoggingAdapter]] to log in the name of the mongo manger object
  */
class DiffPersistence(diffCollection:  MongoCollection[DiffModel], implicit val log: LoggingAdapter)
    extends MongoPersistence[DiffModel] {

  override protected def createWriteRequest(request: MongoRequest) = {
    val PersistDiffs(diffs: List[DiffModel], _) = request
    val writes: List[WriteModel[_ <: DiffModel]] = for {
      diff <- diffs
    } yield ReplaceOneModel(
      Document("commitId" -> diff.identifier, "projectId" -> diff.projectId, "new_path" -> diff.new_path),
      diff, UpdateOptions().upsert(true))

    log.debug(s"CommitDiff write request received, ${diffs.size} commitDiffs being sent to mongoDB")
    diffCollection.bulkWrite(writes, BulkWriteOptions().ordered(false)).asInstanceOf[SingleObservable[Any]]
  }

  override protected def createLoadRequest(request: MongoRequest) = {
    request match {
      case LoadDiff(projectId: String, _, identifierOption: Option[String], _) =>
        identifierOption match {
          case Some(identifier) =>
            log.debug(s"Commit Diff load request received for projectId:$projectId and identifier:$identifier")
            diffCollection.find(and(
              equal("projectId", projectId),
              equal("identifier", identifier))
            )
          case None =>
            log.debug(s"Commit Diff load request received for projectId:$projectId")
            diffCollection.find(equal("projectId", projectId))
        }
      case LoadDiffsIn(projectId, identifierList, _, _) =>
        diffCollection.find(and(equal("projectId", projectId), in("identifier", identifierList:_*)))
    }

  }

  override protected def className = DiffModel.toString
}
