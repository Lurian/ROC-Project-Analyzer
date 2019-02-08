package master.gitlab.service

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import master.gitlab.GitlabConfig
import master.gitlab.GitlabManager.GetFile
import master.gitlab.request.GitlabRequest.{GitlabResponse, RequestError}
import master.gitlab.request.util.GitlabGetRequest
import master.gitlab.util.GitlabError
import master.mongodb.file.FileModel

import scala.util.{Failure, Success}

object GitlabFileService {
  def props(manager: ActorRef)(implicit gitlabConfig: GitlabConfig): Props = {
       Props(GitlabFileService(manager))
  }
}

case class GitlabFileService(manager: ActorRef)(implicit val gitlabConfig: GitlabConfig) extends Actor with ActorLogging {
  import context.dispatcher
  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  override def postStop(): Unit = {
    log.warning(s"GitlabFileService stopping - $self")
    super.postStop()
  }

  def encodeUriSlashes(path: String) : String = {
    path.replace("/", "%2F")
  }

  def commitDiffUri(projectId: String, commitId: String, fileName: String): Uri =
    Uri(s"${gitlabConfig.url}/api/v4/projects/$projectId/repository/files/${encodeUriSlashes(fileName)}/raw")
      .withQuery(Query(s"ref=$commitId"))

  override def receive = waitingForRequest(Map.empty)

  def waitingForRequest(requestMap: Map[Uri, GetFile]): Receive = {
    case request@GetFile(projectId: String, commitId: String, path, _) =>
      val uri = commitDiffUri(projectId, commitId, path)
      manager ! GitlabGetRequest(uri)
      context.become(waitingForRequest(requestMap + (uri -> request)))
    case res@GitlabResponse(uri, _, entity) =>
      val requestOption = requestMap.get(uri)
        if(requestOption.isDefined){
          val request: GetFile = requestOption.get
          val result = Unmarshal(entity).to[String]
          result onComplete {
            case Success(value: String) =>
              request.requester ! FileModel(request.projectId, request.commitId, request.path, value)
            case Failure(err) =>
              throw err
          }
          context.become(waitingForRequest(requestMap - uri))
        } else {
          log.warning(s"Ignoring response as requester was not found response:$res")
        }
    case err@RequestError(uri) =>
      val request = requestMap(uri)
      request.requester ! GitlabError(request.path, err, "GitlabFile error on request")
  }
}