package master.project.task

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingAdapter
import master.mongodb.MongoUtil
import master.mongodb.diff.DiffModel
import master.mongodb.version.VersionModel
import master.mongodb.version.VersionPersistence.LoadVersions
import master.project.task.ChangeAnalysisTaskManager.{CommitChangeAnalysis, VersionChangeAnalysis}
import master.project.task.change.ChangeAnalyzer
import master.project.task.change.ChangeAnalyzer.VersionChangeAnalysisRequest

object ChangeAnalysisTaskManager {
  case class CommitChangeAnalysis()
  case class VersionChangeAnalysis(version: Option[String] = None)
  def props(projectId: String, mongoManager: ActorRef, gitlabManager: ActorRef,
            requester: ActorRef): Props = {
    Props(new ChangeAnalysisTaskManager(projectId, mongoManager, gitlabManager, requester))
  }
}

class ChangeAnalysisTaskManager(projectId: String, mongoManager: ActorRef, gitlabManager: ActorRef, projectManager: ActorRef) extends Actor with ActorLogging {

  var changeAnalyzer: ActorRef = _
  implicit val implicitLog: LoggingAdapter = log

  override def preStart(): Unit = {
    log.info("ChangeManager started")
    this.changeAnalyzer = context.actorOf(ChangeAnalyzer.props(projectId, mongoManager, gitlabManager, projectManager),
      s"ChangeAnalyzer")
  }

  override def postStop(): Unit ={
    log.info("Terminating ChangeManager")
  }

  override def receive = {
    case CommitChangeAnalysis() => ???

    case VersionChangeAnalysis(Some(versionTag)) =>
      log.info("VersionChange Analysis Task Started")
      log.debug("Loading versions")
      mongoManager ! LoadVersions(projectId,
        MongoUtil.aggregatorObserver[VersionModel]({
          (versionList: List[VersionModel]) =>
            val versionDiffMap = (versionList map {(version) => {
              val javaDiffs = version.diffList filter DiffModel.filterNonJavaDiffs
              version -> javaDiffs
            }}).toMap

            val nonEmptyVersionDiffMap = versionDiffMap filter {(kv) => {kv._2.nonEmpty}}
            changeAnalyzer ! VersionChangeAnalysisRequest(nonEmptyVersionDiffMap)
        })
      , Some(versionTag))

    case VersionChangeAnalysis(None) =>
      log.info("VersionChange Analysis Task Started")
      log.debug("Loading versions")
      mongoManager ! LoadVersions(projectId,
        MongoUtil.aggregatorObserver[VersionModel]({
          (versionList: List[VersionModel]) =>
            val versionDiffMap = (versionList map {(version) => {
              val javaDiffs = version.diffList filter DiffModel.filterNonJavaDiffs
              version -> javaDiffs
            }}).toMap

            val nonEmptyVersionDiffMap = versionDiffMap filter {(kv) => {kv._2.nonEmpty}}
            changeAnalyzer ! VersionChangeAnalysisRequest(nonEmptyVersionDiffMap)
        })
      )
  }
}
