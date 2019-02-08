package master.gitlab.service

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.model.{HttpHeader, ResponseEntity, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import master.gitlab.GitlabConfig
import master.gitlab.model.{GitlabPage, TagDTO}
import master.gitlab.request.{GitlabPageRequest, GitlabRequest}
import master.gitlab.service.util.GitlabTagPage
import master.gitlab.util.{Constants, GitlabError}

import scala.util.{Failure, Success}

object GitlabTags {

  def props(projectId: String,
            manager: ActorRef,
            requester: ActorRef)(implicit gitlabConfig: GitlabConfig): Props = {

    Props(new GitlabTags(projectId, manager, requester))
  }
}

class GitlabTags(projectId: String, manager: ActorRef, _requester: ActorRef)(implicit gitlabConfig: GitlabConfig) extends GitlabPageRequest() {
  import context.dispatcher

  protected override def requester: ActorRef = _requester

  override def requestMore(pageList: Seq[String]): Unit = {
    pageList foreach {manager ! GitlabTagPage(projectId, _, Constants.PER_PAGE )}
  }

  override def createUri(page: String): Uri = GitlabTagPage.createUri(projectId, page, Constants.PER_PAGE)

  override def unmarshalAndReturn(entity: ResponseEntity, headers: Seq[HttpHeader], page: String): Unit = {
    val result = Unmarshal(entity).to[List[TagDTO]]
    val totalPagesHeader = headers.find(_.name() == "X-Total-Pages")
    result onComplete {
      case Success(value: List[TagDTO]) =>
        self ! GitlabPage(page, totalPagesHeader.get.value(), value)
      case Failure(err) =>
        throw err
    }
  }

  override def signalizeError(err: GitlabRequest.RequestError): Unit = {
    _requester ! GitlabError(projectId, err, "GitlabTags error on request")
  }
}



