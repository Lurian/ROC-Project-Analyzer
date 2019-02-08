package master.gitlab.request

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.http.scaladsl.model.{HttpHeader, ResponseEntity, Uri}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import master.gitlab.model.{GitlabModel, GitlabPage}
import master.gitlab.request.GitlabRequest.{GitlabResponse, RequestError}
import master.gitlab.request.util.RequestTimeout
import master.gitlab.util.GitlabJsonSupport

abstract class GitlabPageRequest extends Actor with ActorLogging with GitlabJsonSupport {

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))
  var firstPage: String = "1"

  protected def requester: ActorRef

  def requestMore(pageList: Seq[String]): Unit
  def unmarshalAndReturn(entity: ResponseEntity, headers: Seq[HttpHeader], page: String)
  def createUri(page: String): Uri
  def signalizeError(err: RequestError)

  override def preStart(): Unit = {
    log.info("Manager started - " + self)
    requestMore(Seq(firstPage))
    context.become(waitingForFirstReply(Map.empty + (createUri(firstPage) -> (None, firstPage))))
  }

  override def postStop(): Unit = {
  }

  def receive = {
    waitingForFirstReply(Map.empty)
  }


  private def checkIfComplete(pagesReceived: Map[Uri, (Option[List[GitlabModel]], String)]): Unit = {
    if (pagesReceived.forall(x => x._2._1.isDefined)) {
      val returnList: List[GitlabModel] = pagesReceived.values.flatMap({_._1.get}).asInstanceOf[List[GitlabModel]]
      log.debug("Page retrieval successfull! - Entities retrieved: " + returnList.size)
      requester ! returnList
      context.stop(self)
    }
  }

  def waitingForFirstReply(pagesReceived: Map[Uri, (Option[List[GitlabModel]], String)]): Receive = {
    case GitlabPage(_: String, totalPages: String, tagList: List[GitlabModel]) =>
      var newPagesReceived: Map[Uri, (Option[List[GitlabModel]], String)] = pagesReceived + (createUri(firstPage) -> (Some(tagList), firstPage))

      val pageList = (2 to totalPages.toInt) map {
        _.toString
      }
      requestMore(pageList)

      val waitingForPages = for {
        page <- pageList
      } yield createUri(page) -> (None, page)
      newPagesReceived = newPagesReceived ++ waitingForPages

      checkIfComplete(newPagesReceived)

      context.become(waitingForReplies(newPagesReceived))
    case GitlabResponse(uri, headers, entity) =>
      val page: String = pagesReceived(uri)._2
      unmarshalAndReturn(entity, headers, page)
    case RequestTimeout(_) =>
      requestMore(Seq(firstPage))
    case err@RequestError(_) =>
      signalizeError(err)
      context.stop(self)
  }

  def waitingForReplies(pagesReceived: Map[Uri, (Option[List[GitlabModel]], String)]): Receive = {
    case GitlabPage(page: String, _: String, tagList: List[GitlabModel]) =>
      val newPagesReceived: Map[Uri, (Option[List[GitlabModel]], String)] = pagesReceived + (createUri(page) -> (Some(tagList), page))
      checkIfComplete(newPagesReceived)
      context.become(waitingForReplies(newPagesReceived))
    case GitlabResponse(uri, headers, entity) =>
      val page: String = pagesReceived(uri)._2
      unmarshalAndReturn(entity, headers, page)
    case RequestTimeout(uri) =>
      val page: String = pagesReceived(uri)._2
      requestMore(Seq(page))
    case err@RequestError(_) =>
      signalizeError(err)
      context.stop(self)
  }
}