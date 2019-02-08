package master.log

import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import master.log.LogExtractorWorker.{LogExtractFailed, LogExtractRequest, LogExtractResponse}

import scala.io.Source

object LogExtractorWorker {
  case class LogExtractFailed(filePath: String, exception: Exception)
  case class LogExtractRequest(filePath: String)
  case class LogExtractResponse(filePath: String, logLineList: List[LogLine])
  def props(): Props = {
    Props(new LogExtractorWorker())
  }
}

class LogExtractorWorker() extends Actor with ActorLogging {

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  override def receive = {
    case LogExtractRequest(filePath) =>
      log.debug(s"Log extract request received for filepath:$filePath")
      try {
        val logLineList = (Source.fromFile(filePath).getLines map {LogLine(_)}).toList
        sender() ! LogExtractResponse(filePath, logLineList)
      } catch {
        case ex: Exception =>
          log.error(ex.toString)
          sender() ! LogExtractFailed(filePath, ex)
      }
  }
}
