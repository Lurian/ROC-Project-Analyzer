package master.javaParser

import master.javaParser.JavaParserManager.ParseRequest
import master.javaParser.JavaParserWorker.ParserResponse
import master.javaParser.model.{Change, Impact}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import master.mongodb.file.FileModel

object JavaParserWorker {
  case class ParserResponse(impact: Impact, fileModel: FileModel)
  def props(): Props = {
    Props(new JavaParserWorker())
  }
}

class JavaParserWorker() extends Actor
  with ActorLogging {

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  def receive = {
    case ParseRequest(change: Change, fileModel: FileModel, requesterOption: Option[ActorRef]) =>
//      master.log.info("Request received - " + change.fileName)

      val javaImpact: JavaImpact = ChangeChecker.calculateChange(change.toJavaModel, fileModel.file)

      val requester = requesterOption getOrElse sender()
      requester ! ParserResponse(Impact(javaImpact), fileModel)
  }
}
