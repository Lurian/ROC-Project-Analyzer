package master.javaParser

import master.javaParser.JavaParserManager.ParseRequest
import master.javaParser.model.Change

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.routing.RoundRobinPool
import master.mongodb.file.FileModel

object JavaParserManager {
  case class ParseRequest(change: Change, fileModel: FileModel, requesterOption: Option[ActorRef] = None)

  def props(): Props = {
    Props(new JavaParserManager())
  }
}

class JavaParserManager() extends Actor with ActorLogging {

  val router: ActorRef = context.actorOf(
    RoundRobinPool(10).props(JavaParserWorker.props()),
    "gitlabRequester")

  override def preStart(): Unit = {
    log.info("JavaParserManager started - " + self)
  }

  override def receive = {
    case request@ParseRequest(_, _, _) =>
      router forward request
  }
}