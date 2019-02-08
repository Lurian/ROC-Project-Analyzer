package master.project.task

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingAdapter
import master.gitlab.GitlabManager._
import master.gitlab.model.CompareDTO
import master.gitlab.service.GitlabCompareBatch.CompareResponse
import master.mongodb.MongoUtil
import master.mongodb.diff.DiffModel
import master.mongodb.tag.TagModel
import master.mongodb.tag.TagPersistence.LoadTags
import master.mongodb.version.VersionModel
import master.mongodb.version.VersionPersistence.PersistVersions
import master.project.ProjectManager.JsonBugExtraction

object VersionAnalysisTaskManager {
  case class VersionAnalysis(minorVersionOnly: Boolean = false)
  case class VersionAnalysisTaskCompleted()
  def props(projectId: String,
            mongoManager: ActorRef, gitlabManager: ActorRef,
            projectManager: ActorRef): Props = {
    Props(new VersionAnalysisTaskManager(projectId, mongoManager, gitlabManager, projectManager))
  }

  def bySemanticVersioning(tagModel: TagModel): Boolean = {
    val isVersion = """(^\d+\.\d+.\d+$)""".r
    tagModel.name match {
      case isVersion(_*) => true
      case _ => false
    }
  }

  def byMinorVersion(tagModel: TagModel): Boolean = {
    tagModel.name.last.equals('0')
  }

  def getCreationTimeOfNextVersion(orderedTags: List[TagModel], thisVersionDate: Date): Option[Date] = {
    val orderedDates = orderedTags map {_.creationTime}
    orderedDates.find(_.after(thisVersionDate))
  }
}

class VersionAnalysisTaskManager(projectId: String, mongoManager: ActorRef, gitlabManager: ActorRef,
                                 projectManager: ActorRef) extends Actor with ActorLogging {
  import VersionAnalysisTaskManager._
  implicit val implicitLog: LoggingAdapter = log

  override def preStart(): Unit = {
    log.info("VersionAnalyzer started")
  }

  override def postStop(): Unit ={
    log.info("Terminating VersionAnalyzer")
  }

  override def receive = waitingForTask

  def waitingForTask: Receive = {
    case VersionAnalysis(minorVersionOnly) =>
      log.info("Tag Analysis Task Started")
      mongoManager ! LoadTags(projectId, MongoUtil.aggregatorObserver[TagModel](
        (tagList: List[TagModel]) => {
          val semanticVersionTags = tagList filter bySemanticVersioning
          val filteredTags = if(minorVersionOnly) semanticVersionTags filter byMinorVersion else semanticVersionTags
          val orderedTags = filteredTags.sortWith((tagModel, otherTagModel) => VersionModel.orderVersion(tagModel.name, otherTagModel.name))
          val orderedVersionNameList: List[String] = orderedTags map {_.name}
          log.debug(s"Extracted Versions: $orderedVersionNameList")
          val fromToList: List[(String, String)] = orderedVersionNameList.dropRight(1) zip orderedVersionNameList.tail
          log.debug(s"Version Pair (from, to): $fromToList")
          gitlabManager ! GetBatchCompare(projectId, fromToList)

          val tagListWithoutFirstTag = orderedTags.tail
          val initialMap: Map[TagModel, Option[CompareDTO]] = Map.empty ++ (tagListWithoutFirstTag map {_ -> None})
          context.become(waitingForVersionCompare(initialMap, orderedTags))
        })
      )
  }

  def getNextVersionName(orderedTags: List[TagModel], thisVersionDate: Date): Option[String] = {
    val orderedNames = orderedTags
    orderedNames.find(_.creationTime.after(thisVersionDate)) map {_.name}
  }

  private def checkIfComplete(fromToMap: Map[TagModel, Option[CompareDTO]], orderedTags: List[TagModel]): Unit = {
    if(fromToMap.values.forall(_.isDefined)){
      val versionList = fromToMap map {(kv) => {
        val tagModel = kv._1
        val commitList = kv._2.get.commits.map(_.id)
        val diffs: List[DiffModel] = kv._2.get.diffs map {_.asModel(projectId, tagModel.commitId)}
        val filteredDeletedDiffs = diffs filter {!_.deleted_file}
        val fromTime: Option[Date] = getCreationTimeOfNextVersion(orderedTags, tagModel.creationTime)
        val nextVersionNameOption: Option[String] = getNextVersionName(orderedTags, tagModel.creationTime)
        VersionModel(projectId,  tagModel.name, None, nextVersionNameOption, tagModel.creationTime, fromTime, commitList, filteredDeletedDiffs)
      }}
      log.info(s"VERSION ANALYSIS FINISHED - Sending ${versionList.size} versions to mongo database")
      mongoManager ! PersistVersions(versionList.asInstanceOf[List[VersionModel]],
        callback = () => projectManager ! JsonBugExtraction())
      projectManager ! VersionAnalysisTaskCompleted()
      context.become(waitingForTask)
    }
  }

  def waitingForVersionCompare(fromToMap: Map[TagModel, Option[CompareDTO]], orderedTags: List[TagModel]): Receive = {
    case CompareResponse(_, to, compareDTO) =>
      log.debug(s"Version $to compared")
      val tagModel = fromToMap.keys.find(_.name == to).get
      val newFromToMap = fromToMap + (tagModel -> Some(compareDTO))
      checkIfComplete(newFromToMap, orderedTags)
      context.become(waitingForVersionCompare(newFromToMap, orderedTags))
  }
}
