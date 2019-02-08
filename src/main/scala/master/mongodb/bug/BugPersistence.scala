package master.mongodb.bug

import akka.event.LoggingAdapter
import com.mongodb.client.model.WriteModel
import master.mongodb.bug.BugPersistence.{LoadBugs, PersistBugs}
import master.mongodb.{MongoLoadRequest, MongoPersistence, MongoRequest}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.{BulkWriteOptions, ReplaceOneModel, UpdateOptions}
import org.mongodb.scala.{MongoCollection, Observer, SingleObservable}

/**
  * Companion object for [[BugPersistence]]
  */
object BugPersistence {
  case class PersistBugs(bugList: List[BugModel],
                         callback: () => Any = MongoRequest.doNothing) extends MongoRequest(callback)
  case class LoadBugs(override val observer: Observer[BugModel], idOption: Option[String] = None,
                      callback: () => Any = MongoRequest.doNothing) extends MongoLoadRequest(observer, callback)

  /**
    * Constructor of a [[BugPersistence]].
    * @param bugCollection [[MongoCollection]] for accessing the MongoDB storing [[BugPersistence]].
    * @param log [[LoggingAdapter]] to log in the name of the mongo manger object.
    * @return A [[BugPersistence]] instance.
    */
  def apply(bugCollection: MongoCollection[BugModel])(implicit log: LoggingAdapter): BugPersistence =
    new BugPersistence(bugCollection, log)
}

/**
  * Persistence auxiliary class for [[BugModel]]
  * @param bugCollection [[MongoCollection]] for accessing the MongoDB storing [[BugPersistence]].
  * @param log [[LoggingAdapter]] to log in the name of the mongo manger object.
  */
class BugPersistence(bugCollection:  MongoCollection[BugModel], implicit val log: LoggingAdapter)
  extends MongoPersistence[BugModel]{

  private def toWriteModel(bugModel: BugModel): WriteModel[BugModel] = {
    ReplaceOneModel(Document("id" -> bugModel.id),
      bugModel, UpdateOptions().upsert(true))
  }

  override protected def createWriteRequest(request: MongoRequest) = {
    val PersistBugs(bugList: List[BugModel], _) = request
    val writes: List[WriteModel[_ <: BugModel]] = bugList map toWriteModel

    log.debug(s"BugModels request received, ${bugList.size} bugs being sent to mongoDB")
    bugCollection.bulkWrite(writes, BulkWriteOptions().ordered(false)).asInstanceOf[SingleObservable[Any]]
  }

  override protected def createLoadRequest(request: MongoRequest) = {
    val LoadBugs(_, idOption: Option[String], _) = request
    idOption match {
      case Some(bugId) =>
        log.debug(s"BugModel load request received for bugId:$bugId")
        bugCollection.find(
          equal("bugId", bugId),
        ).first
      case None =>
        log.debug(s"BugModel load request received")
        bugCollection.find()
    }
  }

  override protected def className = BugModel.toString
}
