package master.project.task

import java.io.File
import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingAdapter
import master.mongodb.MongoUtil
import master.mongodb.coverage.{BundleCoverage, CoverageModel}
import master.mongodb.coverage.CoveragePersistence.PersistCoverage
import master.mongodb.version.VersionModel
import master.mongodb.version.VersionPersistence.LoadVersions
import master.project.{ExecApi, ExecUtil}
import master.project.task.CoverageAnalysisTaskManager.{CoverageAnalysisCompleted, CoverageAnalysisRequest}
import org.jacoco.core.analysis.{Analyzer, CoverageBuilder}
import org.jacoco.core.tools.ExecFileLoader

object CoverageAnalysisTaskManager {
  case class CoverageAnalysisRequest(dirPath: String, identifierOption: Option[String] = None)
  case class CoverageAnalysisCompleted()
  def props(projectId: String, mongoManager: ActorRef, projectManager: ActorRef): Props = {
    Props(new CoverageAnalysisTaskManager(projectId, mongoManager, projectManager, ExecUtil))
  }
}

class CoverageAnalysisTaskManager(projectId: String, mongoManager: ActorRef, projectManager: ActorRef, execApi: ExecApi)
  extends Actor with ActorLogging {
  implicit val implicitLog: LoggingAdapter = log

  override def preStart(): Unit = {
    log.info("CoverageAnalysisManager started")
  }

  override def postStop(): Unit ={
    log.info("Terminating CoverageAnalysisManager")
  }

  override def receive = {
    /**
      * Single version coverage analysis.
      */
    case CoverageAnalysisRequest(dirPath, Some(identifier)) =>
      val coverageModel = coverageAnalysis(dirPath, identifier)
      log.debug(s"Coverage model extracted, projectId:$projectId, identifier:$identifier")
      mongoManager ! PersistCoverage(coverageModel.get, callback = () => projectManager ! CoverageAnalysisCompleted())

    /**
      * All versions in the DB coverage analysis.
      */
    case CoverageAnalysisRequest(dirPath, None) =>
      mongoManager ! LoadVersions(projectId, observer = MongoUtil.aggregatorObserver[VersionModel]( (versionList) => {
        versionList foreach { (version) =>
          val identifier = version.versionName

          val maybeCoverageModel = coverageAnalysis(dirPath, identifier)
          maybeCoverageModel match {
            case Some(coverageModel) =>
              log.debug(s"Coverage model extracted, projectId:$projectId, identifier:$identifier")
              mongoManager ! PersistCoverage(coverageModel, callback = () => projectManager ! CoverageAnalysisCompleted())
            case None =>
              log.debug(s"No coverage information for projectId:$projectId, identifier:$identifier")
          }
        }
      }))
  }

  /**
    * Extract coverage data in the project folder to an [[CoverageModel]] object.
    * @param dirPath Directory path to the project.
    * @param identifier Identifier of the project version being analyzed.
    */
  private def coverageAnalysis(dirPath: String, identifier: String): Option[CoverageModel] = {
    try {
      val bundleCoverage = extractCoverageData(dirPath, identifier)
      val coverageModel = CoverageModel(projectId, new Date(), identifier, bundleCoverage)
      Some(coverageModel)
    } catch {
      case ex: Exception =>
        log.error(s"Error when extracting coverage data from projectId:$projectId, identifier:$identifier")
        log.error(ex.getMessage)
        None
    }
  }

  def extractCoverageData(dirPath: String, identifier: String): BundleCoverage = {
    log.info(s"Extracting Coverage data from $dirPath/$identifier")
    val executionDataFile = new File(execApi.changeSlashesToFileSeparator(s"$dirPath/$identifier/target/jacoco.exec"))
    val classesDirectory = new File(execApi.changeSlashesToFileSeparator(s"$dirPath/$identifier/target/classes"))

    val execFileLoader = new ExecFileLoader
    execFileLoader.load(executionDataFile)
    val coverageBuilder = new CoverageBuilder
    val analyzer = new Analyzer(execFileLoader.getExecutionDataStore, coverageBuilder)
    analyzer.analyzeAll(classesDirectory)
    BundleCoverage(coverageBuilder.getBundle(identifier))
  }
}
