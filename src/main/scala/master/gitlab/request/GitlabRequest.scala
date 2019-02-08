package master.gitlab.request

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, Stash, SupervisorStrategy}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.ServerError
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import master.gitlab.request.GitlabRequest.{GitlabResponse, RequestError}
import master.gitlab.request.util.{GitlabGetRequest, RequestTimeout}
import master.gitlab.util.PrivateTokenHeader

object GitlabRequest {
  case class GitlabGet(uri: Uri)
  case class RequestError(uri: Uri)
  case class GitlabResponse(uri: Uri, headers: Seq[HttpHeader], entity: ResponseEntity)
  def props(privateToken: String): Props = {
    Props(new GitlabRequest(privateToken))
  }
}

class GitlabRequest(privateToken: String) extends Actor
  with ActorLogging with Stash{

  import akka.pattern.pipe
  import context.dispatcher
  override val supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy() { case _: Exception  => Restart }

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))
  val http = Http(context.system)

  private def doRequest(gitlabUri: Uri): Unit = {
    http.singleRequest(HttpRequest(uri = gitlabUri)
    .withHeaders(PrivateTokenHeader(privateToken))
    ).pipeTo(self)
  }

  override def postStop(): Unit = {
    log.debug("GitlabRequest actor terminating...")
  }

  def receive = {
    case GitlabGetRequest(uri: Uri) =>
      log.debug("Request received - " + uri)
      doRequest(uri)
      context.become(waitingForReply(sender(), uri),
        discardOld = false)
  }

  def waitingForReply(requester: ActorRef, uri: Uri): Receive = {
    // Case Success
    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      log.debug("HttpResponse success - sending to: " + requester)
      requester ! GitlabResponse(uri, headers, entity)
      unstashAll()
      context.unbecome()
    // Case Timeout
    case HttpResponse(code: ServerError, _, entity, _) =>
      log.error(s"Server Error with code$code!")
      entity.dataBytes.runWith(Sink.ignore)
      requester ! RequestTimeout(uri)
      unstashAll()
      context.unbecome()
    // Case Server Error
    case HttpResponse(code, _, entity, _) =>
      log.error(s"Request to failed, response code: $code, uri: $uri")
      entity.dataBytes.runWith(Sink.ignore)
      requester ! RequestError(uri)
      unstashAll()
      context.unbecome()
    case _ =>
      stash()
  }
}