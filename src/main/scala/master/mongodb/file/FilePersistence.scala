package master.mongodb.file

import akka.event.LoggingAdapter
import master.mongodb.{MongoLoadRequest, MongoPersistence, MongoRequest}
import master.mongodb.file.FilePersistence.{LoadFile, PersistFile}
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.UpdateOptions
import org.mongodb.scala.{MongoCollection, Observer, SingleObservable}

/**
  * Companion object for for [[FilePersistence]]
  */
object FilePersistence {
  case class PersistFile(file: FileModel,
                         callback: () => Any = MongoRequest.doNothing) extends MongoRequest(callback)
  case class LoadFile(projectId: String, identifier: String, path: String, override val observer: Observer[FileModel],
                      callback: () => Any = MongoRequest.doNothing) extends MongoLoadRequest(observer, callback)

  /**
    * Constructor of a [[FilePersistence]] instance.
    * @param fileCollection - [[MongoCollection]] for accessing the MongoDB storing [[FileModel]]
    * @param log - LoggingAdapter to log in the name of the mongoManager object
    * @return new [[FilePersistence]] instance.
    */
  def apply(fileCollection: MongoCollection[FileModel])(implicit log: LoggingAdapter): FilePersistence =
    new FilePersistence(fileCollection, log)
}

/**
  * Persistence auxiliary class for [[FileModel]]
  * @param fileCollection - [[MongoCollection]] for accessing the MongoDB storing [[FileModel]]
  * @param log - LoggingAdapter to log in the name of the mongoManager object
  */
class FilePersistence(fileCollection:  MongoCollection[FileModel], implicit val  log: LoggingAdapter)
  extends MongoPersistence[FileModel] {

  override protected def createWriteRequest(request: MongoRequest) = {
    val PersistFile(file: FileModel, _) = request
    log.debug(s"File write request received for projectId:${file.projectId}," +
      s" identifier:${file.identifier}, filePath:${file.path}")

    // Sending write request to mongo
    fileCollection.replaceOne(
      and(
        and(
          equal("projectId", file.projectId),
          equal("path", file.path)),
        equal("identifier", file.identifier)),
      file,
      UpdateOptions().upsert(true)
    ).asInstanceOf[SingleObservable[Any]]
  }

  override protected def createLoadRequest(request: MongoRequest) = {
    val LoadFile(projectId: String, identifier: String, path: String, _, _) = request
    log.debug(s"File load request received for projectId:$projectId, identifier:$identifier, filePath:$path")
    fileCollection.find(
      and(
        and(
          equal("projectId", projectId),
          equal("path", path)),
        equal("identifier", identifier)
      )).first()
  }

  override protected def className = FileModel.toString()
}
