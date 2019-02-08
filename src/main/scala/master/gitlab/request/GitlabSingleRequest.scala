package master.gitlab.request

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.http.scaladsl.model.ResponseEntity
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import master.gitlab.request.GitlabRequest.{GitlabResponse, RequestError}
import master.gitlab.request.util.RequestTimeout
import master.gitlab.util.GitlabJsonSupport

abstract class GitlabSingleRequest extends Actor with ActorLogging with GitlabJsonSupport {
  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  protected def requester: ActorRef

  def doRequest(): Unit
  def unmarshalAndReturn(entity: ResponseEntity)
  def signalizeError(error: RequestError)

  override def preStart(): Unit = {
    doRequest()
  }

  override def postStop(): Unit = {
  }

  def receive = {
    case GitlabResponse(_, _, entity) =>
      log.debug("GitlabResponse received")
      unmarshalAndReturn(entity)
    case RequestTimeout(_) =>
      log.debug("Timeout received - Retrying")
      doRequest()
    case err@RequestError(_) =>
      signalizeError(err)
      context.stop(self)
  }
}