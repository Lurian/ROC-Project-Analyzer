package master.gitlab.service

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import master.gitlab.GitlabConfig
import master.gitlab.model.CompareDTO
import master.gitlab.request.GitlabRequest.RequestError
import master.gitlab.request.GitlabSingleRequest
import master.gitlab.request.util.GitlabGetRequest
import master.gitlab.util.GitlabError

import scala.util.{Failure, Success}

object GitlabCompare {
  def props(projectId: String,
            from: String,
            to: String,
            manager: ActorRef,
            requester: ActorRef)(implicit gitlabConfig: GitlabConfig): Props = {

    def compareUri: Uri = Uri(s"${gitlabConfig.url}/api/v4/projects/$projectId/repository/compare")
      .withQuery(Query(s"from=$from&to=$to"))
    Props(new GitlabCompare(compareUri, from, to, manager, requester))
  }
}

case class GitlabCompare(compareUri: Uri, from: String, to: String, manager: ActorRef, _requester: ActorRef) extends GitlabSingleRequest {

  import context.dispatcher

  override protected def requester: ActorRef = _requester

  override def doRequest(): Unit = manager ! GitlabGetRequest(compareUri)

  override def unmarshalAndReturn(entity: ResponseEntity): Unit = {
    println(entity)
    val result = Unmarshal(entity).to[CompareDTO]
    result onComplete {
      case Success(value: CompareDTO) =>
        requester ! value
        context.stop(self)
      case Failure(err) =>
        throw err
    }
  }

  override def signalizeError(error: RequestError): Unit = {
    _requester ! GitlabError(s"$from/$to", error, "GitlabCompare error on request")
  }
}



