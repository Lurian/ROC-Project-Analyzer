package master.gitlab.service

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import master.gitlab.GitlabConfig
import master.gitlab.model.{CommitChanges, DiffDTO}
import master.gitlab.request.GitlabRequest.RequestError
import master.gitlab.request.GitlabSingleRequest
import master.gitlab.request.util.GitlabGetRequest
import master.gitlab.util.GitlabError

import scala.util.{Failure, Success}

object GitlabCommitDiff {
    def props(projectId: String,
              commitId: String,
              manager: ActorRef,
              requester: ActorRef)(implicit gitlabConfig: GitlabConfig): Props = {
      val commitDiffUri: Uri =
        Uri(s"${gitlabConfig.url}/api/v4/projects/$projectId/repository/commits/$commitId/diff")
      Props( new GitlabCommitDiff(commitDiffUri, commitId, manager, requester))
    }
}

case class GitlabCommitDiff(commitDiffUri: Uri, commitId: String, manager: ActorRef, _requester: ActorRef) extends GitlabSingleRequest  {
  import context.dispatcher

  override protected def requester: ActorRef = _requester

  override def doRequest(): Unit =  manager ! GitlabGetRequest(commitDiffUri)

  override def unmarshalAndReturn(entity: ResponseEntity): Unit = {
    val result = Unmarshal(entity).to[List[DiffDTO]]
    result onComplete {
      case Success(value: List[DiffDTO]) =>
        requester ! CommitChanges(commitId, value)
      case Failure(err) =>
        throw err
    }
  }

  override def signalizeError(error: RequestError): Unit = {
    _requester ! GitlabError(commitId, error, "GitlabCommitDiff error on request")
  }
}



