package master.log

import java.io.File

import master.javaParser.JavaParserManager
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.routing.RoundRobinPool
import master.log.LogAnalyzer.LogAnalysisRequest
import master.log.LogExtractorWorker.{LogExtractFailed, LogExtractRequest, LogExtractResponse}
import master.mongodb.log.LogPersistence.PersistLogs
import master.mongodb.log.LogModel
import master.mongodb.version.VersionModel

object LogAnalyzer {
  case class LogAnalysisRequest(dirPath: String, versionList: List[VersionModel])

  def props(projectId: String, mongoManager: ActorRef): Props = {
    Props(new LogAnalyzer(projectId, mongoManager))
  }
}

class LogAnalyzer(projectId: String, mongoManager: ActorRef) extends Actor with ActorLogging {

  val router: ActorRef = context.actorOf(
    RoundRobinPool(10).props(LogExtractorWorker.props()),
    "logExtractors")

  override def preStart(): Unit = {
    log.info(s"LogAnalyzer started with projectId:$projectId")
  }

  override def receive = waitingForRequest

  def waitingForRequest: Receive = {
    case LogAnalysisRequest(dirPath, versionList) =>
      log.debug(s"Received log analysis request for dirPath:$dirPath")
      val fileNames = getListOfFileNames(dirPath)
      for (fileName <- fileNames) {
        router ! LogExtractRequest(fileName)
      }

      val fileMap: Map[String, Boolean] = (fileNames map {_ -> false}).toMap
      if(fileNames.nonEmpty)
        context.become(waitingForExtract(versionList, fileMap))
      else
        log.debug("No files found on dir.")
  }

  def waitingForExtract(versionList: List[VersionModel], fileMap: Map[String, Boolean]): Receive = {
    case LogExtractResponse(filePath, logLineList) =>
      log.debug(s"Received log extract response for filePath:$filePath")
      val orderedVersionList = versionList.sortWith((v1,v2) =>
        v1.fromDate.after(v2.fromDate)
      )
      val groupByVersionMap : Map[Option[VersionModel], Seq[LogLine]] = logLineList.groupBy((logLine) => {
        orderedVersionList.find(_.fromDate.before(logLine.date))
      })

      groupByVersionMap filter {_._1.isDefined} foreach { (kv) => {
        val versionModel = kv._1.get
        val logLineList = kv._2.toList
        val logModelList = logLineList map {(logLine) => LogModel(versionModel.projectId, versionModel.versionName, logLine)}
        mongoManager ! PersistLogs(logModelList)
      }}

      val newFileMap = fileMap + (filePath -> true)
      val remaining = newFileMap.values count {!_}
      log.debug(s"Files remaining: $remaining")
//      for((value,key) <- newFileMap) {
//         log.debug(s"$value -> $key")
//      }
      if(newFileMap.values.reduce(_&&_)){
        log.info("FINISHED EXTRACTION")
        context.become(waitingForRequest)
      }
      context.become(waitingForExtract(versionList, newFileMap))

    case LogExtractFailed(filePath, ex) =>
      val newFileMap = fileMap + (filePath -> true)
      log.debug(s"SKIPPING $filePath because request failed")
      context.become(waitingForExtract(versionList, newFileMap))
  }

  def getListOfFileNames(dir: String):List[String] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory) {
      d.listFiles.filter(_.isFile) map {_.getAbsolutePath} toList
    } else {
      List[String]()
    }
  }
}