package master.mongodb.commit

import akka.event.LoggingAdapter
import master.mongodb.commit.CommitImpactPersistence.{LoadCommitImpact, PersistCommitImpact}
import master.mongodb.{MongoLoadRequest, MongoPersistence, MongoRequest}
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.UpdateOptions
import org.mongodb.scala.{MongoCollection, Observer, SingleObservable}

/**
  * Companion object for [[CommitImpactPersistence]]
  */
object CommitImpactPersistence {
  case class PersistCommitImpact(commitImpact: CommitImpact,
                                 callback: () => Any = MongoRequest.doNothing) extends MongoRequest(callback)
  case class LoadCommitImpact(projectId: String, override val observer: Observer[CommitImpact], commitIdOption: Option[String] = None,
                              callback: () => Any = MongoRequest.doNothing) extends MongoLoadRequest(observer, callback)

  /**
    * Constructor of a [[CommitImpactPersistence]].
    * @param commitImpactCollection [[MongoCollection]] for accessing the MongoDB storing [[CommitImpact]].
    * @param log [[LoggingAdapter]] to log in the name of the mongo manger object.
    * @return A [[CommitPersistence]] instance.
    */
  def apply(commitImpactCollection: MongoCollection[CommitImpact])(implicit log: LoggingAdapter): CommitImpactPersistence =
    new CommitImpactPersistence(commitImpactCollection, log)
}

class CommitImpactPersistence(commitImpactCollection:  MongoCollection[CommitImpact], implicit val log: LoggingAdapter)
  extends MongoPersistence[CommitImpact]{

  override protected def createWriteRequest(request: MongoRequest) = {
    val PersistCommitImpact(commitImpact: CommitImpact, _) = request
    log.debug(s"CommitImpact write request received for projectId:${commitImpact.projectId}, commitId:${commitImpact.commitId}")
    commitImpactCollection.replaceOne(
      and(
        equal("projectId", commitImpact.projectId),
        equal("commitId", commitImpact.commitId)),
      commitImpact,
      UpdateOptions().upsert(true)
    ).asInstanceOf[SingleObservable[Any]]
  }

  override protected def createLoadRequest(request: MongoRequest) = {
    val LoadCommitImpact(projectId: String, _, commitIdOption: Option[String], _) = request
    commitIdOption match {
      case Some(commitId) =>
        log.debug(s"CommitImpact load request received for projectId:$projectId, commitId:$commitId")
        commitImpactCollection.find(
          and(
            equal("projectId", projectId),
            equal("commitId", commitId)),
        ).first
      case None =>
        log.debug(s"CommitImpact load request received for projectId:$projectId")
        commitImpactCollection.find(
          equal("projectId", projectId),
        )
    }
  }

  override protected def className = CommitImpact.toString
}
