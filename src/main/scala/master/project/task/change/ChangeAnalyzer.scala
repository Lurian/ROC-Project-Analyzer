package master.project.task.change

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingAdapter
import akka.routing.RoundRobinPool
import master.javaParser.JavaParserManager
import master.mongodb.ChangeImpact
import master.mongodb.commit.CommitImpact
import master.mongodb.commit.CommitImpactPersistence.PersistCommitImpact
import master.mongodb.diff.DiffModel
import master.mongodb.version.VersionImpactPersistence.PersistVersionImpact
import master.mongodb.version.{VersionImpact, VersionModel}
import master.project.task.change.ChangeWorker.{ChangeAnalysisError, ChangeAnalysisRequest}

object ChangeAnalyzer {
  case class GetCurrentIdentifierMap()
  case class CommitChangeAnalysisRequest(commitMapDiff: Map[String, List[DiffModel]])
  case class VersionChangeAnalysisRequest(versionMapDiff: Map[VersionModel, List[DiffModel]])
  case class VersionChangeAnalysisTaskCompleted()

  def props(projectId: String, mongoManager: ActorRef, gitlabManager: ActorRef, projectManager: ActorRef,
            routerOption: Option[ActorRef] = None): Props = {
    Props(new ChangeAnalyzer(projectId, mongoManager, gitlabManager, projectManager, routerOption))
  }
}

class ChangeAnalyzer(projectId: String, mongoManager: ActorRef, gitlabManager: ActorRef, projectManager: ActorRef,
                     routerOption: Option[ActorRef]) extends Actor with ActorLogging {
  import ChangeAnalyzer._
  val javaParserManager: ActorRef = context.actorOf(JavaParserManager.props(), s"JavaParserManager-$projectId")
  implicit val implicitLog: LoggingAdapter = log

  val router: ActorRef = routerOption.getOrElse(context.actorOf(
    RoundRobinPool(1).props(ChangeWorker.props(projectId, mongoManager, gitlabManager, javaParserManager)),
    "gitlabRequester"))

  override def preStart(): Unit = {
    log.info(s"ChangeAnalyzer started with projectId:$projectId")
  }

  override def receive = waitingForImpact(Map.empty)

  //TODO: refact this
  def waitingForImpact(identifierTypeMap: Map[String, Either[CommitImpact.type , VersionImpact.type]]): Receive ={
    case CommitChangeAnalysisRequest(commitMapDiff: Map[String, List[DiffModel]]) =>
      log.debug("CommitChange Analysis Request received")
      commitMapDiff foreach {(kv) =>
        val commitModelId = kv._1
        val commitDiffList = kv._2
        router ! ChangeAnalysisRequest(commitDiffList, commitModelId)
      }
      val newIdentifierMap = commitMapDiff.keys.map(_ -> Left(CommitImpact)).toMap
      context.become(waitingForImpact(newIdentifierMap))

    case VersionChangeAnalysisRequest(versionMapDiff) =>
      log.debug("VersionChange Analysis Request received")
      versionMapDiff foreach {(kv) => {
        val version = kv._1
        val commitDiffList = kv._2
        router ! ChangeAnalysisRequest(commitDiffList, version.versionName)
      }}
      val newIdentifierMap = versionMapDiff.keys.map(_.versionName).map(_ -> Right(VersionImpact)).toMap
      context.become(waitingForImpact(newIdentifierMap))

    case impact@ChangeImpact(_, identifier, _) =>
      identifier match {
        case _ if identifierTypeMap(identifier).isLeft =>
          mongoManager ! PersistCommitImpact(CommitImpact(impact))
          val newIdentifierMap = identifierTypeMap - identifier
          context.become(waitingForImpact(newIdentifierMap))
        case _ if identifierTypeMap(identifier).isRight =>
          mongoManager ! PersistVersionImpact(VersionImpact(impact))
          val newIdentifierMap = identifierTypeMap - identifier

          if(newIdentifierMap.isEmpty) projectManager ! VersionChangeAnalysisTaskCompleted()
          context.become(waitingForImpact(newIdentifierMap))
        case _ =>
          log.error("Not left nor right - Impact Change Analysis")
      }

    case ChangeAnalysisError(identifier, err) =>
      log.error(err.errMsg)
      log.warning(s"Error when impact analyzing identifier:$identifier, ignoring this identifier")
      val newIdentifierMap = identifierTypeMap - identifier
      context.become(waitingForImpact(newIdentifierMap))

    case GetCurrentIdentifierMap =>
      sender() ! identifierTypeMap
  }
}