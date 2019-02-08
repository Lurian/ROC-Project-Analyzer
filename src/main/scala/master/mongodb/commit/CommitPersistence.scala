package master.mongodb.commit

import akka.event.LoggingAdapter
import com.mongodb.client.model.WriteModel
import master.mongodb.commit.CommitPersistence.{LoadCommits, LoadCommitsIn, PersistCommits}
import master.mongodb.{MongoLoadRequest, MongoPersistence, MongoRequest}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.{and, equal, in}
import org.mongodb.scala.model.{BulkWriteOptions, ReplaceOneModel, UpdateOptions}
import org.mongodb.scala.{MongoCollection, Observer, SingleObservable}

/**
  * Companion object of [[CommitPersistence]]
  */
object CommitPersistence {
  case class PersistCommits(commitList: List[CommitModel],
                            callback: () => Any = MongoRequest.doNothing) extends MongoRequest(callback)
  case class LoadCommits(projectId: String, override val observer: Observer[CommitModel], filterMergeCommits: Option[Boolean] = None,
                         callback: () => Any = MongoRequest.doNothing) extends MongoLoadRequest(observer, callback)
  case class LoadCommitsIn(projectId: String, identifierList: List[String], override val observer: Observer[CommitModel],
                         callback: () => Any = MongoRequest.doNothing) extends MongoLoadRequest(observer, callback)

  /**
    * Constructor of a [[CommitPersistence]].
    * @param commitCollection [[MongoCollection]] for accessing the MongoDB storing [[CommitModel]].
    * @param log [[LoggingAdapter]] to log in the name of the mongo manger object.
    * @return A [[CommitPersistence]] instance.
    */
  def apply(commitCollection: MongoCollection[CommitModel])(implicit log: LoggingAdapter): CommitPersistence =
    new CommitPersistence(commitCollection, log)
}

/**
  * Persistence auxiliary class for [[CommitModel]]
  * @param commitCollection [[MongoCollection]] for accessing the MongoDB storing [[CommitModel]].
  * @param log [[LoggingAdapter]] to log in the name of the mongo manger object.
  */
class CommitPersistence(commitCollection:  MongoCollection[CommitModel], implicit val log: LoggingAdapter)
  extends MongoPersistence[CommitModel]{

  override protected def createWriteRequest(request: MongoRequest) = {
    val PersistCommits(commitList: List[CommitModel], _) = request
    log.debug(s"Commit write request received, ${commitList.size} commits being sent to mongoDB")
    val writes: List[WriteModel[_ <: CommitModel]] = for {
      commit <- commitList
    } yield ReplaceOneModel(Document("id" -> commit.id, "projectId" -> commit.projectId), commit, UpdateOptions().upsert(true))

    commitCollection.bulkWrite(writes, BulkWriteOptions().ordered(false)).asInstanceOf[SingleObservable[Any]]
  }

  override protected def createLoadRequest(request: MongoRequest) = {
    request match {
      case LoadCommits(projectId, _, filterMergeCommitsOption: Option[Boolean], _) =>
        filterMergeCommitsOption match {
          case Some(filterOutMergeCommit) =>
            log.debug(s"Commit Load Request received for projectId:$projectId with isMerge:$filterOutMergeCommit filter")
            commitCollection.find(and(equal("projectId", projectId), equal("isMerge", !filterOutMergeCommit)))
          case None =>
            log.debug(s"Commit Load Request received for projectId:$projectId")
            commitCollection.find(equal("projectId", projectId))
        }
      case LoadCommitsIn(projectId, identifierList, _, _) =>
        commitCollection.find(and(equal("projectId", projectId), in("id", identifierList:_*)))
    }
  }

  override protected def className = CommitModel.toString
}
