package master.mongodb.coverage

import akka.event.LoggingAdapter
import master.mongodb.coverage.CoveragePersistence.{LoadCoverage, PersistCoverage}
import master.mongodb.{MongoLoadRequest, MongoPersistence, MongoRequest}
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.UpdateOptions
import org.mongodb.scala.{MongoCollection, Observer, SingleObservable}

/**
  * Companion object for [[CoveragePersistence]]
  */
object CoveragePersistence {
  case class PersistCoverage(coverageModel: CoverageModel,
                             callback: () => Any = MongoRequest.doNothing) extends MongoRequest(callback)
  case class LoadCoverage(projectId: String, override val observer: Observer[CoverageModel],
                          identifierOption: Option[String] = None,
                          callback: () => Any = MongoRequest.doNothing) extends MongoLoadRequest(observer, callback)

  /**
    * Constructor of a [[CoveragePersistence]].
    * @param coverageCollection [[MongoCollection]] for accessing the MongoDB storing [[CoverageModel]].
    * @param log [[LoggingAdapter]] to log in the name of the mongo manger object.
    * @return A [[CoveragePersistence]] instance.
    */
  def apply(coverageCollection: MongoCollection[CoverageModel])(implicit log: LoggingAdapter): CoveragePersistence =
    new CoveragePersistence(coverageCollection, log)
}

/**
  * Persistence auxiliary class for [[CoverageModel]]
  * @param coverageCollection [[MongoCollection]] for accessing the MongoDB storing [[CoverageModel]].
  * @param log [[LoggingAdapter]] to log in the name of the mongo manger object.
  */
class CoveragePersistence(coverageCollection:  MongoCollection[CoverageModel], implicit val log: LoggingAdapter)
  extends MongoPersistence[CoverageModel]{

  override protected def createWriteRequest(request: MongoRequest) = {
    val PersistCoverage(coverage: CoverageModel, _) = request
    log.debug(s"Coverage write request received for projectId:${coverage.projectId}," +
      s" identifier:${coverage.identifier}")
    coverageCollection.replaceOne(
      and(
        equal("projectId", coverage.projectId),
        equal("identifier", coverage.identifier)),
      coverage,
      UpdateOptions().upsert(true)
    ).asInstanceOf[SingleObservable[Any]]
  }

  override protected def createLoadRequest(request: MongoRequest) = {
    val LoadCoverage(projectId: String, _, identifierOption: Option[String], _) = request
    identifierOption match {
      case Some(identifier) =>
        log.debug(s"Coverage load request received for projectId:$projectId, identifier:$identifier")
        coverageCollection.find(
          and(
            equal("projectId", projectId),
            equal("identifier", identifier)
          )).first()
      case None =>
        log.debug(s"Coverage load request received for projectId:$projectId")
        coverageCollection.find(
          equal("projectId", projectId))
    }
  }

  override protected def className = CoverageModel.toString()
}
