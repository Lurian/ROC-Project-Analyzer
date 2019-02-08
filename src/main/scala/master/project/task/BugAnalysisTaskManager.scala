package master.project.task

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingAdapter
import master.mongodb.MongoUtil
import master.mongodb.bug.BugModel
import master.mongodb.bug.BugPersistence.{LoadBugs, PersistBugs}
import master.mongodb.commit.CommitModel
import master.mongodb.commit.CommitPersistence.{LoadCommits, LoadCommitsIn, PersistCommits}
import master.mongodb.version.VersionModel
import master.mongodb.version.VersionPersistence.{LoadVersions, PersistVersions}
import master.project.task.BugAnalysisTaskManager.{BugAnalysis, BugAnalysisTaskCompleted}
import master.project.task.ChangeAnalysisTaskManager.VersionChangeAnalysis

object BugAnalysisTaskManager {
  case class BugAnalysis(tagOption: Option[String] = None, commitIdList: Option[List[String]] = None)
  case class BugAnalysisTaskCompleted()
  case class CommitBugAnalysisTaskCompleted()
  def props(projectId: String, mongoManager: ActorRef, gitlabManager: ActorRef,
            projectManager: ActorRef): Props = {
    Props(new BugAnalysisTaskManager(projectId, mongoManager, gitlabManager, projectManager))
  }
}

class BugAnalysisTaskManager(projectId: String, mongoManager: ActorRef,
                             gitlabManager: ActorRef, projectManager: ActorRef) extends Actor with ActorLogging {
  implicit val implicitLog: LoggingAdapter = log
  override def preStart(): Unit = {
    log.info("BugAnalysisManager started")
  }

  override def postStop(): Unit ={
    log.info("Terminating BugAnalysisManager")
  }

  def checkIfIsBugFixCommit(title: String, bugIdList: List[String]): Boolean = {
    val titleLC = title.toLowerCase
    val containsBugFixTag = titleLC.contains("correção") ||  titleLC.contains("correcao") ||
      titleLC.contains("bug")
    val possibleBugIdentifiers = getPossibleBugIdentifiers(title)
    val containsAnBugId = bugIdList exists {(id) => possibleBugIdentifiers.contains(id)}
    if(!containsBugFixTag && containsAnBugId){
      log.debug(s"containsBugFixTag:$containsBugFixTag")
      log.debug(s"possibleBugIdentifiers:$possibleBugIdentifiers")
      log.debug(s"containsAnBugId:$containsAnBugId")
    }
    containsBugFixTag && containsAnBugId
  }

  def getPossibleBugIdentifiers(commitTitle: String): List[String] ={
    val numberRegex = """\d+""".r
    // Filtering identifiers greater than 9 to reduce the false positive rate
    // as these numbers are common and not always mean bug identifiers.
    numberRegex.findAllMatchIn(commitTitle) map {_.toString()} filter {_.toInt > 9} toList
  }

  def byRelevance(bug: BugModel): Boolean = {
    bug.severity != "melhoria" && bug.resolution != "INVALID" &&  bug.resolution != "DUPLICATE"
  }

  override def receive = {
    case BugAnalysis(Some(tag), Some(commitIdList)) =>
      log.debug(s"Bug Analysis request received for projectId:$projectId and tag:$tag")
      mongoManager ! LoadBugs(MongoUtil.aggregatorObserver[BugModel]({ (bugList: List[BugModel]) => {
        mongoManager ! LoadCommitsIn(projectId, commitIdList, MongoUtil.aggregatorObserver[CommitModel]({ (commitList: List[CommitModel]) => {
          mongoManager ! LoadVersions(projectId, MongoUtil.aggregatorObserver[VersionModel]({ (versionList: List[VersionModel]) => {
            val filteredBugList = bugList filter byRelevance
            val bugAnalyzedCommitList: List[CommitModel] = commitList map toAnalyzedCommitModel(filteredBugList)
            val setOfPossibleBugIdentifiers: Set[String] = (bugAnalyzedCommitList filter {_.isBugFix} flatMap {_.bugIds.getOrElse(List())}).toSet

            log.debug(s"From ${commitList.size} commits, ${bugAnalyzedCommitList count {_.isBugFix}} are bug fix commits")
            log.info(s"COMMIT BUG ANALYSIS FINISHED - ${bugAnalyzedCommitList.size} commits sent to update")
            mongoManager ! PersistCommits(bugAnalyzedCommitList)

            val filteredByCommitBugList = filteredBugList filter {(bug) => setOfPossibleBugIdentifiers.contains(bug.id)}
            log.debug(s"Updating ${versionList.size} versions with data from bugs and commits")

            // Updating bugs with their respectively version names which match the time of bug creation
            val fromToList: List[(VersionModel, VersionModel)] = createFromToListFromVersions(versionList)
            val newBugListWithVersionTag = for { bug <- filteredByCommitBugList }
              yield BugModel(bug, getVersionNameWhereBugWasCreated(bug, fromToList))
            mongoManager ! PersistBugs(newBugListWithVersionTag)

            // Updating versions to match the bugFixCommits count and bugCount
            val bugVersionsAnalyzed = versionList map toAnalyzedVersionModel(bugAnalyzedCommitList, newBugListWithVersionTag)

            log.info(s"VERSION BUG ANALYSIS FINISHED - ${bugVersionsAnalyzed.size} versions sent to update")
            mongoManager ! PersistVersions(bugVersionsAnalyzed,
              callback = () => projectManager ! VersionChangeAnalysis())
            projectManager ! BugAnalysisTaskCompleted()
          }
          }), Some(tag))
        }
        }))
      }
      }))
    case BugAnalysis(None, None) =>
      log.debug(s"Bug Analysis request received for projectId:$projectId")
      mongoManager ! LoadBugs(MongoUtil.aggregatorObserver[BugModel]({ (bugList: List[BugModel]) => {
        mongoManager ! LoadCommits(projectId, MongoUtil.aggregatorObserver[CommitModel]({ (commitList: List[CommitModel]) => {
          mongoManager ! LoadVersions(projectId, MongoUtil.aggregatorObserver[VersionModel]({ (versionList: List[VersionModel]) => {
            val filteredBugList = bugList filter byRelevance
            val bugAnalyzedCommitList: List[CommitModel] = commitList map toAnalyzedCommitModel(filteredBugList)
            val setOfPossibleBugIdentifiers: Set[String] = (bugAnalyzedCommitList filter {_.isBugFix} flatMap {_.bugIds.getOrElse(List())}).toSet

            log.debug(s"From ${commitList.size} commits, ${bugAnalyzedCommitList count {_.isBugFix}} are bug fix commits")
            log.info(s"COMMIT BUG ANALYSIS FINISHED - ${bugAnalyzedCommitList.size} commits sent to update")
            mongoManager ! PersistCommits(bugAnalyzedCommitList)
//            projectManager ! CommitBugAnalysisTaskCompleted()

            val filteredByCommitBugList = filteredBugList filter {(bug) => setOfPossibleBugIdentifiers.contains(bug.id)}
            log.debug(s"Updating ${versionList.size} versions with data from bugs and commits")

            // Updating bugs with their respectively version names which match the time of bug creation
            val fromToList: List[(VersionModel, VersionModel)] = createFromToListFromVersions(versionList)
            val newBugListWithVersionTag = for { bug <- filteredByCommitBugList }
              yield BugModel(bug, getVersionNameWhereBugWasCreated(bug, fromToList))
            mongoManager ! PersistBugs(newBugListWithVersionTag)

            // Updating versions to match the bugFixCommits count and bugCount
            val bugVersionsAnalyzed = versionList map toAnalyzedVersionModel(bugAnalyzedCommitList, newBugListWithVersionTag)

            log.info(s"VERSION BUG ANALYSIS FINISHED - ${bugVersionsAnalyzed.size} versions sent to update")
            mongoManager ! PersistVersions(bugVersionsAnalyzed,
              callback = () => projectManager ! VersionChangeAnalysis())
            projectManager ! BugAnalysisTaskCompleted()
          }
          }))
        }
        }))
      }
      }))
  }

  def toAnalyzedVersionModel(bugAnalyzedCommitList: List[CommitModel], newBugListWithVersionTag: List[BugModel])(oldVersion: VersionModel): VersionModel = {
    val commitsOfTheVersion = bugAnalyzedCommitList filter {  oldVersion.idCommitList contains _.id }
    val bugFixCommitCount : Int = commitsOfTheVersion map { (commit) => if (commit.isBugFix) 1 else 0 } sum

    val bugCount: Int = newBugListWithVersionTag count { oldVersion.versionName == _.versionName.getOrElse("") }
    oldVersion.addBugAnalysis(bugFixCommitCount, bugCount)
  }

  def toAnalyzedCommitModel(filteredBugList: List[BugModel])(oldCommit: CommitModel) : CommitModel = {
      val possibleIdentifiers: List[String] = getPossibleBugIdentifiers(oldCommit.title)
      val commitBugIdList = filteredBugList filter {(bug) => possibleIdentifiers.contains(bug.id)} map {_.id}

      val filteredBugIdList = filteredBugList map {_.id}
      val isBugFix: Boolean = checkIfIsBugFixCommit(oldCommit.title, filteredBugIdList)

      // The isBugFix property must be true and bug identifiers must have been found for the commit to be marked
      // as a bug fix and updated with possible bug identifiers
      if(commitBugIdList.nonEmpty && isBugFix){
        CommitModel(oldCommit, isBugFix,  Some(commitBugIdList))
      } else {
        // Commits may not follow the bug fix tag convention and still have bug identifiers on its title
        // the warning below warns about commits which fits the scenario above
        if(commitBugIdList.nonEmpty && !isBugFix){
          log.warning(s"Commit with commitId:${oldCommit.id} have bug ids on its title," +
            s" bugIds:$commitBugIdList, but no bugFix tag. title:${oldCommit.title}")
        }
        // If isBugFix was set as true and no bug identifiers were found this mean a inconsistency between the bug fix
        // checker and the bug identifier retriever. This signal a implementation problem!
        if(isBugFix) log.warning(s"isBugFix is true, but no bugId was found. Something is not right! ${oldCommit.title}")
        val notABugFix = false
        CommitModel(oldCommit, notABugFix, None)
      }
    }

  def createFromToListFromVersions(versionList: List[VersionModel]): List[(VersionModel, VersionModel)] ={
    val orderedVersionList: List[VersionModel] = versionList.sortWith { (x1, x2) =>
      VersionModel.orderVersion(x1.versionName, x2.versionName)
    }
    orderedVersionList.dropRight(1) zip orderedVersionList.tail
  }

  def getVersionNameWhereBugWasCreated(bugModel: BugModel, fromToList: List[(VersionModel, VersionModel)]): Option[String] ={
    val bugCreationTime = bugModel.creationTime

    val firstVersionCreationTime = fromToList.head._1.fromDate
    if(bugCreationTime.before(firstVersionCreationTime)){
      return None
    }

    val lastVersion = fromToList.last._2
    val lastVersionCreationTime = lastVersion.fromDate
    if(bugCreationTime.after(lastVersionCreationTime)){
      return Some(lastVersion.versionName)
    }

    val inBetweenVersionOption = fromToList find {(vv) => {
      val fromDateTime = vv._1.fromDate
      val toDateTime = vv._2.fromDate
      bugCreationTime.before(toDateTime) && bugCreationTime.after(fromDateTime)
    }}

    if(inBetweenVersionOption.isDefined){
      Some(inBetweenVersionOption.get._1.versionName)
    } else {
      None
    }
  }
}