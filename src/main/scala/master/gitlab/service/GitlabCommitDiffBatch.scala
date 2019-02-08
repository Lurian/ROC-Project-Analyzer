package master.gitlab.service

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import master.gitlab.GitlabConfig
import master.gitlab.model.{CommitChanges, DiffDTO}
import master.gitlab.request.GitlabBatchRequest
import master.gitlab.request.GitlabRequest.RequestError
import master.gitlab.request.util.GitlabGetRequest
import master.gitlab.util.GitlabError

import scala.util.{Failure, Success}

object GitlabCommitDiffBatch {
  def props(projectId: String,
            commitIdList: List[String],
            manager: ActorRef,
            requester: ActorRef)(implicit gitlabConfig: GitlabConfig): Props = {

    Props(new GitlabCommitDiffBatch(projectId, commitIdList, manager, requester))
  }
}

case class GitlabCommitDiffBatch(projectId: String, commitIdList: List[String],
                                 manager: ActorRef, _requester: ActorRef)(implicit val gitlabConfig: GitlabConfig) extends GitlabBatchRequest  {
  import context.dispatcher

  def createUri(commitId: String) =
    Uri(s"${gitlabConfig.url}/api/v4/projects" +
    s"/$projectId/repository/commits/$commitId/diff")

  var commitDiffMap: Map[Uri, (Option[List[DiffDTO]], String)] =
    Map.empty ++ (for {commitId <- commitIdList} yield createUri(commitId) -> (None, commitId))

  override protected def requester: ActorRef = _requester

  override def doRequest(uri: Option[Uri]): Unit = {
    uri match {
      case None => commitDiffMap.filter(_._2._1.isEmpty).keys foreach { manager ! GitlabGetRequest(_) }
      case Some(uriParameter: Uri) => manager ! GitlabGetRequest(uriParameter)
    }
  }

  private def checkIfComplete(): Unit = {
    if(commitDiffMap forall {_._2._1.isDefined}) {
      context.stop(self)
    }
  }

  override def unmarshalAndReturn(entity: ResponseEntity, uri: Uri): Unit = {
    val result = Unmarshal(entity).to[List[DiffDTO]]
    result onComplete {
      case Success(value: List[DiffDTO]) =>
        val commitId: String = commitDiffMap(uri)._2
        commitDiffMap = commitDiffMap + (uri -> (Some(value), commitId))
        requester ! CommitChanges(commitId, value)
        checkIfComplete()
      case Failure(err) =>
        throw err
    }
  }

  override def signalizeError(err: RequestError): Unit = {
    _requester ! GitlabError(projectId, err, "GitlabCommitDiffBatch error on request")
  }
}



