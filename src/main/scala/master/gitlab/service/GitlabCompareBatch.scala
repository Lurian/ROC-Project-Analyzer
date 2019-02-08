package master.gitlab.service

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import master.gitlab.GitlabConfig
import master.gitlab.model.CompareDTO
import master.gitlab.request.util.GitlabGetRequest
import master.gitlab.request.{GitlabBatchRequest, GitlabRequest}
import master.gitlab.service.GitlabCompareBatch.CompareResponse
import master.gitlab.util.GitlabError

import scala.util.{Failure, Success}

object GitlabCompareBatch {
  def props(projectId: String,
            fromToList: List[(String, String)],
            manager: ActorRef,
            requester: ActorRef)(implicit gitlabConfig: GitlabConfig): Props = {

    Props(new GitlabCompareBatch(projectId, fromToList, manager, requester))
  }

  case class CompareResponse(from: String, to: String, compareDTO: CompareDTO)

}

case class GitlabCompareBatch(projectId: String, fromToList: List[(String, String)],
                                 manager: ActorRef, _requester: ActorRef)(implicit val gitlabConfig: GitlabConfig) extends GitlabBatchRequest  {
  import context.dispatcher

  private def createUri(from: String, to: String) =
    Uri(s"${gitlabConfig.url}/api/v4/projects/$projectId/repository/compare")
      .withQuery(Query(s"from=$from&to=$to"))

  var compareMap: Map[Uri, (Option[CompareDTO], (String, String))] =
    Map.empty ++ (for {(from, to) <- fromToList} yield createUri(from, to) -> (None, (from, to)))

  override protected def requester: ActorRef = _requester

  override def doRequest(uri: Option[Uri]): Unit = {
    uri match {
      case None => compareMap.filter(_._2._1.isEmpty).keys foreach { manager ! GitlabGetRequest(_) }
      case Some(uriParameter: Uri) => manager ! GitlabGetRequest(uriParameter)
    }
  }

  private def checkIfComplete(): Unit = {
    if(compareMap forall {_._2._1.isDefined}) {
      context.stop(self)
    }
  }

  override def unmarshalAndReturn(entity: ResponseEntity, uri: Uri): Unit = {
    val result = Unmarshal(entity).to[CompareDTO]
    result onComplete {
      case Success(value: CompareDTO) =>
        val (from, to) = compareMap(uri)._2
        compareMap = compareMap + (uri -> (Some(value), (from, to)))
        requester ! CompareResponse(from, to, value)
        checkIfComplete()
      case Failure(err) =>
        log.error(s"Error when requesting $uri")
        throw err
        context.stop(self)
    }
  }

  override def signalizeError(err: GitlabRequest.RequestError): Unit = {
    _requester ! GitlabError(projectId, err, "GitlabCompareBatch error on request")
  }
}



