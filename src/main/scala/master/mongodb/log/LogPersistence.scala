package master.mongodb.log

import akka.event.LoggingAdapter
import com.mongodb.client.model.WriteModel
import master.mongodb.MongoRequest
import master.mongodb.log.LogPersistence.{LoadLogs, PersistLogs}
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.{BulkWriteOptions, InsertOneModel}
import org.mongodb.scala.{MongoCollection, Observer}

/**
  * Companion object for for [[LogPersistence]]
  */
@Deprecated
object LogPersistence {
  case class PersistLogs(tagList: List[LogModel],
                         callback: () => Any = MongoRequest.doNothing) extends MongoRequest(callback)
  case class LoadLogs(projectId: String, observer: Observer[LogModel],
                      callback: () => Any = MongoRequest.doNothing) extends MongoRequest(callback)

  /**
    * Constructor of a [[LogPersistence]] instance.
    * @param logCollection - [[MongoCollection]] for accessing the MongoDB storing [[LogModel]]
    * @param log - LoggingAdapter to log in the name of the mongoManager object
    * @return new [[LogPersistence]] instance.
    */
  def apply(logCollection: MongoCollection[LogModel])(implicit log: LoggingAdapter): LogPersistence =
    new LogPersistence(logCollection, log)
}

/**
  * Persistence auxiliary class for [[LogModel]]
  * @param logCollection - [[MongoCollection]] for accessing the MongoDB storing [[LogModel]]
  * @param log - LoggingAdapter to log in the name of the mongoManager object
  */
@Deprecated
class LogPersistence(logCollection:  MongoCollection[LogModel], log: LoggingAdapter) {

  /**
    * Handles a load request of a [[LogModel]]
    * @param request Write Request
    */
  def persist(request: PersistLogs): Unit = {
    val PersistLogs(logList, _) = request
    log.debug(s"Log write request received, ${logList.size} logLines being sent to mongoDB")

    // Creating a BulkWrite request for mongo
    val writes: List[WriteModel[_ <: LogModel]] = for {
      logModel <- logList
    } yield InsertOneModel(logModel)

    // Sending request to mongo
    val mongoRequest = logCollection.bulkWrite(writes, BulkWriteOptions().ordered(false))

    // Subscribing debug observers
//    mongoRequest.subscribe(bulkWriteCompletedLogObserver, errorLogObserver)
//    )
  }

  /**
    * Handles a load request of a [[LogModel]]
    * @param request Load Request
    */
  def load(request: LoadLogs): Unit = {
    val LoadLogs(projectId, observer, _) = request
    log.debug(s"Log load request received for projectId:$projectId")

    // Creating the mongo load request
    val mongoRequest = logCollection.find(equal("projectId", projectId))

    // Subscribing request observer
    mongoRequest.subscribe(observer)
  }
}
