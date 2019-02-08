package master.gitlab.service

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.model.{HttpHeader, ResponseEntity, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import master.gitlab.GitlabConfig
import master.gitlab.model.{CommitDTO, GitlabPage}
import master.gitlab.request.GitlabPageRequest
import master.gitlab.request.GitlabRequest.RequestError
import master.gitlab.service.util.GitlabCommitPage
import master.gitlab.util.{Constants, GitlabError}

import scala.util.{Failure, Success}

object GitlabCommits {

  def props(projectId: String,
            manager: ActorRef,
            requester: ActorRef)(implicit gitlabConfig: GitlabConfig): Props = {
    Props(new GitlabCommits(projectId, manager, requester))
  }
}

class GitlabCommits(projectId: String, manager: ActorRef, _requester: ActorRef)(implicit gitlabConfig: GitlabConfig) extends GitlabPageRequest() {
  import context.dispatcher

  protected override def requester: ActorRef = _requester

  override def requestMore(pageList: Seq[String]): Unit = {
    pageList foreach {manager ! GitlabCommitPage(projectId, _, Constants.PER_PAGE )}
  }

  override def createUri(page: String): Uri = GitlabCommitPage.createUri(projectId, page, Constants.PER_PAGE)

  override def unmarshalAndReturn(entity: ResponseEntity, headers: Seq[HttpHeader], page: String): Unit = {
    val result = Unmarshal(entity).to[List[CommitDTO]]
    val totalPagesHeader = headers.find(_.name() == "X-Total-Pages")
    result onComplete {
      case Success(value: List[CommitDTO]) =>
        self ! GitlabPage(page, totalPagesHeader.get.value(), value)
      case Failure(err) =>
        throw err
    }
  }

  override def signalizeError(err: RequestError): Unit = {
    _requester ! GitlabError(projectId, err, "GitlabCommits error on request")
  }
}




