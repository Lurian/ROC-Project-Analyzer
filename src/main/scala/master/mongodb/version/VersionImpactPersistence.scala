package master.mongodb.version

import akka.event.LoggingAdapter
import master.mongodb.version.VersionImpactPersistence.{LoadVersionImpact, PersistVersionImpact}
import master.mongodb.{MongoLoadRequest, MongoPersistence, MongoRequest}
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.UpdateOptions
import org.mongodb.scala.{MongoCollection, Observer, SingleObservable}

/**
  * Companion object for for [[VersionPersistence]]
  */
object VersionImpactPersistence {
  case class PersistVersionImpact(versionImpact: VersionImpact,
                                  callback: () => Any = MongoRequest.doNothing) extends MongoRequest(callback)
  case class LoadVersionImpact(projectId: String, override val observer: Observer[VersionImpact], tagOption: Option[String] = None,
                               callback: () => Any = MongoRequest.doNothing) extends MongoLoadRequest(observer, callback)

  /**
    * Constructor of a [[VersionPersistence]] instance.
    * @param versionImpactCollection - [[MongoCollection]] for accessing the MongoDB storing [[VersionModel]]
    * @param log - LoggingAdapter to log in the name of the mongoManager object
    * @return new [[VersionPersistence]] instance.
    */
  def apply(versionImpactCollection: MongoCollection[VersionImpact])(implicit log: LoggingAdapter): VersionImpactPersistence =
    new VersionImpactPersistence(versionImpactCollection, log)
}

/**
  * Persistence auxiliary class for [[VersionImpact]]
  * @param versionImpactCollection - [[MongoCollection]] for accessing the MongoDB storing [[VersionModel]]
  * @param log - LoggingAdapter to log in the name of the mongoManager object
  */
class VersionImpactPersistence(versionImpactCollection:  MongoCollection[VersionImpact], implicit val log: LoggingAdapter)
  extends MongoPersistence[VersionImpact]{

  override protected def createWriteRequest(request: MongoRequest) = {
    val PersistVersionImpact(versionImpact: VersionImpact, _) = request
    log.debug(s"VersionImpact write request received for projectId:${versionImpact.projectId}, tag:${versionImpact.tag}")
    versionImpactCollection.replaceOne(
      and(
        equal("projectId", versionImpact.projectId),
        equal("tag", versionImpact.tag)),
      versionImpact,
      UpdateOptions().upsert(true)
    ).asInstanceOf[SingleObservable[Any]]
  }

  override protected def createLoadRequest(request: MongoRequest) = {
    val LoadVersionImpact(projectId: String, observer: Observer[VersionImpact], tagOption: Option[String], _) = request
    tagOption match {
      case Some(tag) =>
        log.debug(s"VersionImpact load request received for projectId:$projectId, tag:$tag")
        versionImpactCollection.find(
          and(
            equal("projectId", projectId),
            equal("tag", tag)),
        ).first
      case None =>
        log.debug(s"CommitImpact load request received for projectId:$projectId")
        versionImpactCollection.find(
          equal("projectId", projectId),
        )
    }
  }

  override protected def className = VersionImpact.toString
}
