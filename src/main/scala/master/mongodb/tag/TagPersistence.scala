package master.mongodb.tag

import akka.event.LoggingAdapter
import com.mongodb.client.model.WriteModel
import master.mongodb.tag.TagPersistence.{LoadTags, PersistTags}
import master.mongodb.{MongoLoadRequest, MongoPersistence, MongoRequest}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.{BulkWriteOptions, ReplaceOneModel, UpdateOptions}
import org.mongodb.scala.{MongoCollection, Observer, SingleObservable}

/**
  * Companion object for for [[TagPersistence]]
  */
object TagPersistence {
  case class PersistTags(tagList: List[TagModel],
                         callback: () => Any = MongoRequest.doNothing) extends MongoRequest(callback)
  case class LoadTags(projectId: String, override val observer: Observer[TagModel],
                      callback: () => Any = MongoRequest.doNothing) extends MongoLoadRequest(observer, callback)

  /**
    * Constructor of a [[TagPersistence]] instance.
    * @param tagCollection - [[MongoCollection]] for accessing the MongoDB storing [[TagModel]]
    * @param log - LoggingAdapter to log in the name of the mongoManager object
    * @return new [[TagPersistence]] instance.
    */
  def apply(tagCollection: MongoCollection[TagModel])(implicit log: LoggingAdapter): TagPersistence =
    new TagPersistence(tagCollection, log)
}

/**
  * Persistence auxiliary class for [[TagModel]]
  * @param tagCollection - [[MongoCollection]] for accessing the MongoDB storing [[TagModel]]
  * @param log - LoggingAdapter to log in the name of the mongoManager object
  */
class TagPersistence(tagCollection:  MongoCollection[TagModel], implicit val log: LoggingAdapter)
  extends MongoPersistence[TagModel]{

  override protected def createWriteRequest(request: MongoRequest) = {
    val PersistTags(tagList, _) = request
    log.debug(s"${tagList.size} tags being sent to mongoDB")

    // Creating a BulkWrite request for mongo
    val writes: List[WriteModel[_ <: TagModel]] = for {
      tag <- tagList
    } yield ReplaceOneModel(Document("name" -> tag.name, "projectId" -> tag.projectId), tag, UpdateOptions().upsert(true))

    tagCollection.bulkWrite(writes, BulkWriteOptions().ordered(false)).asInstanceOf[SingleObservable[Any]]
  }

  override protected def createLoadRequest(request: MongoRequest) = {
    val LoadTags(projectId, _, _) = request
    // Creating the mongo load request
    tagCollection.find(equal("projectId", projectId))
  }

  override protected def className: String = TagModel.toString()
}
