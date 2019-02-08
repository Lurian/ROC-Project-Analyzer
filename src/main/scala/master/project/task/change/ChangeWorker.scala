package master.project.task.change

import master.javaParser.JavaParserManager.ParseRequest
import master.javaParser.JavaParserWorker.ParserResponse
import master.javaParser.model.{Change, Impact}

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import master.gitlab.GitlabManager.GetFile
import master.gitlab.util.GitlabError
import master.mongodb.ChangeImpact
import master.mongodb.diff.DiffModel
import master.mongodb.file.FileModel
import master.mongodb.file.FilePersistence.{LoadFile, PersistFile}
import org.mongodb.scala.Observer
import master.project.task.change.ChangeWorker.{ChangeAnalysisError, ChangeAnalysisRequest}

object ChangeWorker {
  case class ChangeAnalysisRequest(commitDiffList: List[DiffModel], commitId: String)
  case class ChangeAnalysisError(identifier: String, err: GitlabError)
  def props(projectId: String, mongoManager: ActorRef, gitlabManager: ActorRef, javaParserManager: ActorRef): Props = {
    Props(new ChangeWorker(projectId, mongoManager, gitlabManager, javaParserManager))
  }
}

class ChangeWorker(projectId: String, mongoManager: ActorRef, gitlabManager: ActorRef,
                   javaParserManager: ActorRef) extends Actor with ActorLogging with Stash {

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  def receive = waitingForRequest

  def waitingForRequest: Receive = {
    case ChangeAnalysisRequest(commitDiffList: List[DiffModel], identifier: String) =>
      log.debug("Change Analysis Request received - " + commitDiffList.head.identifier)

      def observer(fileName: String): Observer[FileModel] = new Observer[FileModel] {
        var fileModelOption: Option[FileModel] = None
        override def onNext(result: FileModel): Unit = fileModelOption = Some(result)
        override def onError(e: Throwable): Unit = throw e
        override def onComplete(): Unit = {
          if(fileModelOption.isDefined){
            log.debug("File Retrieved")
            self ! fileModelOption.get
          } else {
            log.debug(s"File not found, requesting on Gitlab, fileName:$fileName")
            gitlabManager ! GetFile(projectId, identifier, fileName, self)
          }
        }
      }

      val commitDiffQty = commitDiffList.size
      log.debug(s"$commitDiffQty commitDiff file requests sent")
      commitDiffList foreach { (commitDiffModel) =>
        mongoManager ! LoadFile(projectId, identifier, commitDiffModel.new_path, observer(commitDiffModel.new_path))
      }

      val changeMap: Map[Change, Option[Impact]] = (commitDiffList map {(commitDiffModel) =>  Change(commitDiffModel.diff, commitDiffModel.new_path) -> None }).toMap
      val waitingFilesMap: Map[String, FileModel] = (commitDiffList map {(commitDiffModel) => commitDiffModel.new_path -> FileModel(projectId, identifier, commitDiffModel.new_path, "")}).toMap
      context.become(waitingForFile(changeMap, waitingFilesMap, identifier, sender()))
  }

  def isComplete(newChangeMap: Map[Change, Option[Impact]]): Boolean = newChangeMap.values forall { _.isDefined }

  def isFileOnWaitingList(waitingFilesMap: Map[String, FileModel])(projectId: String, identifier: String, fileName: String): Boolean = {
    val optionFileModel: Option[FileModel] = waitingFilesMap.get(fileName)
    if(optionFileModel.isDefined){
      val fileModel = optionFileModel.get
      return fileModel.projectId == projectId &&
        fileModel.identifier == identifier &&
        fileModel.path == fileName
    }
    false
  }

  def waitingForFile(changeMap: Map[Change, Option[Impact]], waitingFilesMap: Map[String, FileModel], identifier: String, requester: ActorRef): Receive = {
    case fileModel@FileModel(fileModelProjectId, fileModelIdentifier, fileName, _) =>
      log.debug(s"File received - $fileName")
      mongoManager ! PersistFile(fileModel)
      if(isFileOnWaitingList(waitingFilesMap)(fileModelProjectId, fileModelIdentifier, fileName)){
        val change = changeMap.keys.find(_.fileName==fileName).get
        javaParserManager ! ParseRequest(change, fileModel, Some(self))
      }
      // Do nothing if is not on the waiting list

    case err@GitlabError(id,_,_) =>
      log.error(s"GitlabError for id:$id")
      requester ! ChangeAnalysisError(identifier, err)
      context.become(waitingForRequest)
      unstashAll()

    case ParserResponse(impact, fileModel) =>
      log.debug(s"Impact received - ${fileModel.path}")

      if(isFileOnWaitingList(waitingFilesMap)(fileModel.projectId, fileModel.identifier, fileModel.path)){
        val change = changeMap.keys.find(_.fileName==fileModel.path).get
        val newChangeMap: Map[Change, Option[Impact]] = changeMap + (change -> Some(impact))

        if(isComplete(newChangeMap)){
          log.debug(s"Changes of identifier:$identifier successfully analyzed")
          val impactList: List[Impact] = (newChangeMap.values map { _.get }).toList
          val changeImpact: ChangeImpact = ChangeImpact(projectId, identifier, impactList)
          requester ! changeImpact
          context.become(waitingForRequest)
          unstashAll()
        } else {
          context.become(waitingForFile(newChangeMap, waitingFilesMap, identifier, requester))
        }
      }
      // Do nothing if is not on the waiting list

    case msg@_ =>
      log.debug(s"stashing ${msg.getClass}")
      stash()
  }
}
