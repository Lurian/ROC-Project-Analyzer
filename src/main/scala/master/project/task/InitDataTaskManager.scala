package master.project.task

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingAdapter
import master.gitlab.GitlabManager._
import master.gitlab.model.{CommitChanges, CommitDTO, CompareDTO, TagDTO}
import master.gitlab.util.GitlabError
import master.mongodb.MongoUtil
import master.mongodb.commit.CommitPersistence.PersistCommits
import master.mongodb.diff.DiffModel
import master.mongodb.diff.DiffPersistence.{LoadDiff, PersistDiffs}
import master.mongodb.tag.TagModel
import master.mongodb.tag.TagPersistence.{LoadTags, PersistTags}
import master.mongodb.version.VersionModel
import master.mongodb.version.VersionPersistence.PersistVersions
import master.project._
import master.project.task.InitDataTaskManager.{InitDataLoadTaskCompleted, LoadInitData, LoadTagData, TagDataLoadTaskCompleted}

object InitDataTaskManager {
  case class LoadInitData()
  case class InitDataLoadTaskCompleted()
  case class CommitRetrievalCompleted()
  case class TagRetrievalCompleted()
  case class TagDataLoadTaskCompleted(commitIdList: List[String])

  case class LoadTagData(tag: String)
  def props(projectId: String, mongoManager: ActorRef,
            gitlabManager: ActorRef, projectManager: ActorRef): Props = {
    Props(new InitDataTaskManager(projectId, mongoManager, gitlabManager, projectManager))
  }
}

class InitDataTaskManager(projectId: String, mongoManager: ActorRef,
                          gitlabManager: ActorRef, projectManager: ActorRef)
  extends Actor with ActorLogging {
  implicit val implicitLog: LoggingAdapter = log
  override def receive = waitingForTasks

  override def preStart(): Unit = {
    log.info("InitDataManager started")
  }

  override def postStop(): Unit ={
    log.info("Terminating InitDataManager")
  }

  def waitingForTasks: Receive = {
    case LoadInitData() =>
      gitlabManager ! GetTags(projectId)
      gitlabManager ! GetCommits(projectId)
      context.become(waitingForInitLoad(Set(TagLoadTask(false), CommitLoadTask(false))))
    case LoadTagData(tag: String) =>
      log.debug("Gotcha")
      gitlabManager ! GetTag(projectId, tag)
      mongoManager ! LoadTags(projectId, MongoUtil.sendBackObserver[TagModel](self))
      context.become(waitingForTagLoad(None, None, Set.empty, tag))
    case _ =>
      log.debug("damn")
  }

  def waitingForTagLoad(targetTagOption: Option[TagModel], lastTagOption: Option[TagModel], taskList: Set[ProjectTask], targetTagName: String): Receive = {
    case tag@TagDTO(_,_) =>
      val targetTagModel = tag.asModel(projectId)
      log.debug(targetTagModel.toString)
      if(lastTagOption.isDefined) {
        gitlabManager ! GetCompare(projectId, lastTagOption.get.name, targetTagModel.name)
        context.become(waitingForTagLoad(Some(targetTagModel), lastTagOption, Set.empty, targetTagName))
      }
      else context.become(waitingForTagLoad(Some(targetTagModel), None, Set.empty, targetTagName))
      mongoManager ! PersistTags(List(targetTagModel))

    case list@List(_: TagModel, _*) =>
      log.debug(list.toString())
      val tagList = list.asInstanceOf[List[TagModel]] filter VersionAnalysisTaskManager.bySemanticVersioning filter VersionAnalysisTaskManager.byMinorVersion
      val filteredTagList = tagList.filter((tag: TagModel) => VersionModel.orderVersion(tag.name, targetTagName))
      val lastTag = filteredTagList.sortWith((tag: TagModel, otherTag: TagModel) => !VersionModel.orderVersion(tag.name, otherTag.name)).head
//      val lastTag = tagList.find((tag: TagModel) => tag.name == "1.53.2").get
      log.debug(lastTag.toString)
      if(targetTagOption.isDefined) {
        gitlabManager ! GetCompare(projectId, lastTag.name, targetTagOption.get.name)
        context.become(waitingForTagLoad(targetTagOption, Some(lastTag), Set.empty, targetTagName))
      }
      else context.become(waitingForTagLoad(None, Some(lastTag), Set.empty, targetTagName))
      log.debug(lastTag.toString)

    case compare@CompareDTO(_,_,_,_,_) =>
      // Extracting Version
      val tagModel = targetTagOption.get
      val previousVersionNameOption: Option[String] = Some(lastTagOption.get.name)
      val commitIdList = compare.commits.map(_.id)
      val diffs: List[DiffModel] = compare.diffs map {_.asModel(projectId, tagModel.commitId)}
      val filteredDeletedDiffs = diffs filter {!_.deleted_file}
      val versionModel = VersionModel(projectId,  tagModel.name, previousVersionNameOption, None, tagModel.creationTime, None, commitIdList, filteredDeletedDiffs)
      mongoManager ! PersistVersions(List(versionModel))

      //Persisting Commits related to the version
      val commitModelList = compare.commits map { _.asModel(projectId) }
      mongoManager ! PersistCommits(commitModelList)

      gitlabManager ! GetBatchCommitDiff(projectId, commitIdList)
      val mapOfCommitDiffs: Map[String, Boolean] = (commitIdList map {_ -> false}).toMap
      val commitDiffLoadTask = CommitDiffLoadTask(mapOfCommitDiffs)
      context.become(waitingForTagLoad(targetTagOption, lastTagOption, Set(commitDiffLoadTask), targetTagName))

    case commitChanges@CommitChanges(commitId, commitDiffList) =>
      val qtdCommitDiffs = commitDiffList.size
      if (commitDiffList.isEmpty)  log.info(s"No commitDiffs found for commitId:$commitId")
      else {
        log.info(s"Sending $qtdCommitDiffs CommitDiffs with CommitId:$commitId to mongo database")
        mongoManager ! PersistDiffs(commitChanges.asModel(projectId))
      }

      // Calculating New Task List
      val commitDiffTask: CommitDiffLoadTask = taskList.find({
        case _: CommitDiffLoadTask => true
        case _ => false
      }).get.asInstanceOf[CommitDiffLoadTask]
      val newCompletedMap = commitDiffTask.completedMap + (commitId -> true)
      val newTaskList = taskList - commitDiffTask + CommitDiffLoadTask(newCompletedMap)
      val isTaskListCompleted: Boolean = newTaskList map { _.isComplete } reduce { _&&_ }

      // Checking completion - Updating task list
      if (isTaskListCompleted) {
        log.info("Init Data Task Manager - Waiting for tasks")
        context.become(waitingForTasks)
        val totalQtyCommitDiffs = newCompletedMap.keys.size
        log.info(s"DIFF RETRIEVAL FINISHED - $totalQtyCommitDiffs commits searched")
        projectManager ! TagDataLoadTaskCompleted(commitDiffTask.completedMap.keys.toList)
      }
      else
        context.become(waitingForTagLoad(targetTagOption, lastTagOption, newTaskList, targetTagName))

    case GitlabError(identifier, err, errMsg) =>
      log.error(errMsg)
      throw new Exception(s"GitlabError when requesting identifier:$identifier at uri:${err.uri}")
  }

  def waitingForInitLoad(taskList: Set[ProjectTask]): Receive = {
    case tagList@List(_: TagDTO, _*) =>
      log.info("TAG RETRIEVAL FINISHED - Sending TagList to mongo database")
//      projectManager ! TagRetrievalCompleted()
      val tagModelList = tagList.asInstanceOf[List[TagDTO]] map { _.asModel(projectId) }
      mongoManager ! PersistTags(tagModelList)

      // Calculating New Task List
      val tagLoadTask: TagLoadTask = taskList.find({
        case _: TagLoadTask => true
        case _ => false
      }).get.asInstanceOf[TagLoadTask]
      val newTaskList = taskList - tagLoadTask + TagLoadTask(true)
      val isTaskListCompleted: Boolean = newTaskList map { _.isComplete } reduce { _&&_ }

      // Checking completion - Updating task list
      if (isTaskListCompleted) {
        log.info("Init Data Task Manager - Waiting for tasks")
        context.become(waitingForTasks)
      } else context.become(waitingForInitLoad(newTaskList))

    case commitList@List(_: CommitDTO, _*) =>
      log.info("COMMIT RETRIEVAL FINISHED - Sending CommitList to mongo database")
//      projectManager ! CommitRetrievalCompleted()
      val commitModelList = commitList.asInstanceOf[List[CommitDTO]] map { _.asModel(projectId) }
      mongoManager ! PersistCommits(commitModelList)

      log.debug("Loading commitDiffs to check which commitDiffs are in need of retrieval from master.gitlab")
      mongoManager ! LoadDiff(projectId, MongoUtil.aggregatorObserver[DiffModel]({
        (commitDiffList: List[DiffModel]) => {
          val commitIdsAlreadyWithDiff: Set[String] = (commitDiffList map {_.identifier}).toSet
          val filteredCommitModelList = commitModelList filter { (commit) => !commitIdsAlreadyWithDiff.contains(commit.id) }

          log.debug(s"From ${commitModelList.size} commits, ${commitIdsAlreadyWithDiff.size} were already retrieved and " +
            s"${filteredCommitModelList.size} are going to be retrieved now")

          gitlabManager ! GetBatchCommitDiff(projectId, filteredCommitModelList map {_.id})

          val mapOfCommitDiffs: Map[String, Boolean] = (filteredCommitModelList map {_.id -> false}).toMap
          val commitDiffLoadTask = CommitDiffLoadTask(mapOfCommitDiffs)

          // Calculating New Task List
          val commitLoadTask: CommitLoadTask = taskList.find({
            case _: CommitLoadTask => true
            case _ => false
          }).get.asInstanceOf[CommitLoadTask]
          val newTaskList = taskList - commitLoadTask + CommitLoadTask(true) + commitDiffLoadTask
          val isTaskListCompleted: Boolean = newTaskList map { _.isComplete } reduce { _&&_ }

          // Checking completion - Updating task list
          if (isTaskListCompleted) {
            log.info("Init Data Task Manager - Waiting for tasks")
            context.become(waitingForTasks)
          } else
            context.become(waitingForInitLoad(newTaskList))
        }
      }))

    case commitChanges@CommitChanges(commitId, commitDiffList) =>
      val qtdCommitDiffs = commitDiffList.size
      if (commitDiffList.isEmpty)  log.info(s"No commitDiffs found for commitId:$commitId")
      else {
        log.info(s"Sending $qtdCommitDiffs CommitDiffs with CommitId:$commitId to mongo database")
        mongoManager ! PersistDiffs(commitChanges.asModel(projectId))
      }

      // Calculating New Task List
      val commitDiffTask: CommitDiffLoadTask = taskList.find({
        case _: CommitDiffLoadTask => true
        case _ => false
      }).get.asInstanceOf[CommitDiffLoadTask]
      val newCompletedMap = commitDiffTask.completedMap + (commitId -> true)
      val newTaskList = taskList - commitDiffTask + CommitDiffLoadTask(newCompletedMap)
      val isTaskListCompleted: Boolean = newTaskList map { _.isComplete } reduce { _&&_ }

      // Checking completion - Updating task list
      if (isTaskListCompleted) {
        log.info("Init Data Task Manager - Waiting for tasks")
        context.become(waitingForTasks)
        val totalQtdCommitDiffs = newCompletedMap.keys.size
        log.info(s"DIFF RETRIEVAL FINISHED - $totalQtdCommitDiffs commits searched")
        projectManager ! InitDataLoadTaskCompleted()
      }
      else
        context.become(waitingForInitLoad(newTaskList))
    case GitlabError(identifier, err, errMsg) =>
      log.error(errMsg)
      throw new Exception(s"GitlabError when requesting identifier:$identifier at uri:${err.uri}")
  }
}