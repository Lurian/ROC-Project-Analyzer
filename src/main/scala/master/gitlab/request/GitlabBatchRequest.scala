package master.gitlab.request

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.http.scaladsl.model.{ResponseEntity, Uri}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import master.gitlab.request.GitlabRequest.{GitlabResponse, RequestError}
import master.gitlab.request.util.RequestTimeout
import master.gitlab.util.GitlabJsonSupport

abstract class GitlabBatchRequest extends Actor with ActorLogging with GitlabJsonSupport {
  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  protected def requester: ActorRef

  def doRequest(uri: Option[Uri] = None): Unit
  def unmarshalAndReturn(entity: ResponseEntity, uri: Uri)
  def signalizeError(err: RequestError)

  override def preStart(): Unit = {
    doRequest()
  }

  override def postStop(): Unit = {
  }

  def receive = {
    case GitlabResponse(uri, _, entity) =>
      log.debug("GitlabResponse received")
      unmarshalAndReturn(entity, uri)
    case RequestTimeout(uri) =>
      log.debug("Timeout received - Retrying")
      doRequest(Some(uri))
    case err@RequestError(_) =>
      signalizeError(err)
      context.stop(self)
  }
}