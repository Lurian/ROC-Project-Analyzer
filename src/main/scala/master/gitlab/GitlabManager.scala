package master.gitlab

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.routing.RoundRobinPool
import master.gitlab.request.GitlabRequest
import master.gitlab.service._


object GitlabManager {
  case class GetTags(projectId: String)
  case class GetTag(projectId: String, tag: String)
  case class GetCommits(projectId: String)
  case class GetCompare(projectId: String, from: String, to: String)
  case class GetCommitDiff(projectId: String, commitId: String)
  case class GetBatchCommitDiff(projectId: String, commitIdList: List[String])
  case class GetBatchCompare(projectId: String, fromToList: List[(String, String)] )
  case class GetFile(projectId: String, commitId: String, path: String, requester: ActorRef)

  def props(gitlabConfig: GitlabConfig): Props = {
    Props(new GitlabManager(gitlabConfig))
  }
}

class GitlabManager(gitlabConfig: GitlabConfig) extends Actor with ActorLogging {
  import GitlabManager._
  implicit val gitlabConfigImplicit: GitlabConfig = gitlabConfig

  val defaultRouter: ActorRef = context.actorOf(
    RoundRobinPool(5).props(GitlabRequest.props(gitlabConfig.privateToken)),
    "gitlabDefaultRequester")
  val gitlabFileService: ActorRef = context.actorOf(GitlabFileService.props(defaultRouter),
    s"GitlabFileService")

  override def preStart(): Unit = {
    log.info("Manager started - " + self)
  }

  override def postStop(): Unit = {
    log.debug(s"GitlabManager stopping - $self")
    super.postStop()
  }

  override def receive = {
    case GetTags(projectId: String) =>
      context.actorOf(GitlabTags.props(projectId, defaultRouter, sender()),
        s"GitlabTags-$projectId")
    case GetTag(projectId: String, tag: String) =>
      context.actorOf(GitlabTab.props(projectId, tag, defaultRouter, sender()),
        s"GitlabTags-$projectId")
    case GetCommits(projectId: String) =>
      context.actorOf(GitlabCommits.props(projectId, defaultRouter, sender()),
        s"GitlabCommits-$projectId")
    case GetCommitDiff(projectId: String, commitId: String) =>
      context.actorOf(GitlabCommitDiff.props(projectId, commitId, defaultRouter, sender()),
        s"GitlabCommitDiff-$projectId-$commitId")
    case GetCompare(projectId: String, from: String, to: String) =>
      context.actorOf(GitlabCompare.props(projectId, from, to, defaultRouter, sender()),
        s"GitlabCompare-$projectId-$from-$to")
    case GetBatchCommitDiff(projectId: String, commitIdList: List[String])  =>
      context.actorOf(GitlabCommitDiffBatch.props(projectId, commitIdList, defaultRouter, sender()),
        s"GetBatchCommitDiff-$projectId-" + commitIdList.hashCode())
    case GetBatchCompare(projectId: String, fromToList: List[(String, String)]) =>
      context.actorOf(GitlabCompareBatch.props(projectId, fromToList, defaultRouter, sender()),
        s"GitlabCompare-$projectId-" + fromToList.hashCode())
    case request@GetFile(_,_,_,_) =>
      gitlabFileService ! request
  }
}
