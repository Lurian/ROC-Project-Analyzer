package master.gitlab.service

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import master.gitlab.GitlabConfig
import master.gitlab.model.TagDTO
import master.gitlab.request.GitlabRequest.RequestError
import master.gitlab.request.GitlabSingleRequest
import master.gitlab.request.util.GitlabGetRequest
import master.gitlab.util.GitlabError

import scala.util.{Failure, Success}

object GitlabTab {
  def props(projectId: String,
            tag: String,
            manager: ActorRef,
            requester: ActorRef)(implicit gitlabConfig: GitlabConfig): Props = {

    def tagUri: Uri = Uri(s"${gitlabConfig.url}/api/v4/projects/$projectId/repository/tags/$tag")
    Props(new GitlabTab(tagUri, tag, manager, requester))
  }
}

case class GitlabTab(tagUri: Uri, tag: String, manager: ActorRef, _requester: ActorRef) extends GitlabSingleRequest {

  import context.dispatcher

  override protected def requester: ActorRef = _requester

  override def doRequest(): Unit = manager ! GitlabGetRequest(tagUri)

  override def unmarshalAndReturn(entity: ResponseEntity): Unit = {
    val result = Unmarshal(entity).to[TagDTO]
    result onComplete {
      case Success(value: TagDTO) =>
        requester ! value
      case Failure(err) =>
        throw err
    }
  }

  override def signalizeError(error: RequestError): Unit = {
    _requester ! GitlabError(s"Geting tag $tag", error, "GitlabCompare error on request")
  }
}
