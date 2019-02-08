package master.project.task

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingAdapter
import master.log.LogAnalyzer
import master.log.LogAnalyzer.LogAnalysisRequest
import master.mongodb.MongoUtil
import master.mongodb.version.VersionModel
import master.mongodb.version.VersionPersistence.LoadVersions
import master.project.task.LogAnalysisTaskManager.LogAnalysis

object LogAnalysisTaskManager {
  case class LogAnalysis(dirPath: String)
  def props(projectId: String,
            mongoManager: ActorRef,
            projectManager: ActorRef): Props = {
    Props(new LogAnalysisTaskManager(projectId, mongoManager,  projectManager))
  }
}

class LogAnalysisTaskManager(projectId: String, mongoManager: ActorRef, projectManager: ActorRef) extends Actor with ActorLogging {
  var logAnalyzer: ActorRef = _
  implicit val implicitLog: LoggingAdapter = log

  override def preStart(): Unit = {
    log.info("LogAnalyzer started")
    this.logAnalyzer = context.actorOf(LogAnalyzer.props(projectId, mongoManager),
      s"ChangeAnalyzer")
  }

  override def postStop(): Unit ={
    log.info("Terminating LogAnalyzer")
  }

  override def receive = waitingForTask

  def waitingForTask: Receive = {
    case LogAnalysis(dirPath) =>
      log.info("Tag Analysis Task Started")
      mongoManager ! LoadVersions(projectId, MongoUtil.aggregatorObserver[VersionModel](
        (versionList: List[VersionModel]) =>
          logAnalyzer ! LogAnalysisRequest(dirPath, versionList)
        )
      )
  }

}
