package master.research.method

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingAdapter
import master.javaParser.model.Impact
import master.mongodb.MongoUtil
import master.mongodb.commit.CommitPersistence.LoadCommitsIn
import master.mongodb.commit.{CommitImpact, CommitModel}
import master.mongodb.diff.DiffModel
import master.mongodb.diff.DiffPersistence.LoadDiffsIn
import master.project.task.change.ChangeAnalyzer.CommitChangeAnalysisRequest
import master.project.task.change.ChangeAnalyzerAccumulator
import master.research.method

/**
  * Companion object for [[BugExtractor]]
  */
object BugExtractor {

  case class MethodToBugAnalysis(signatureToBugMap: Map[String, List[String]])

  def props(projectId: String, identifierList: List[String], requester: ActorRef, mongoManager: ActorRef,
            gitlabManager: ActorRef): Props =
    Props(new BugExtractor(projectId, identifierList, requester, mongoManager, gitlabManager))
}

/**
  * Extract information about the bugs detected in each method by analyzing which commits are bug fix commits and the methods
  * impacted on the commits diffs. This is an one request actor with pre-start requisitions.
  * @param projectId    [[String]] project id.
  * @param commitIdList [[List]] of [[String]] with the commits ids.
  * @param requester    [[ActorRef]] actor waiting for the [[method.BugExtractor.MethodToBugAnalysis]].
  * @param mongoManager [[ActorRef]] mongo manager actor ref.
  */
class BugExtractor(projectId: String, commitIdList: List[String], requester: ActorRef, mongoManager: ActorRef, gitlabManager: ActorRef) extends Actor with ActorLogging{
  import BugExtractor.MethodToBugAnalysis
  implicit val implicitLog: LoggingAdapter = log

  /**
    * First step on the workflow: Load the commits models.
    */
  override def preStart(): Unit = {
    log.info("BugExtractor started")
    mongoManager ! LoadCommitsIn(projectId, commitIdList,  MongoUtil.sendBackObserver[CommitModel](self))
  }

  override def receive = waitingForCommitModelLoad()

  /**
    * Second step on the workflow: Receive the commit models and load diffs for the commits which are bug fix
    */
  def waitingForCommitModelLoad(): Receive = {
    case list: List[_] =>
      val commitModelList = list.asInstanceOf[List[CommitModel]]
      log.debug(commitModelList.map(_.id).toString)
      val bugFixIdentifierList = commitModelList.filter(_.isBugFix).map(_.id)
      log.debug(bugFixIdentifierList.toString)
      val commitIdToBugMap = commitModelList map {(commitModel) => commitModel.id -> commitModel.bugIds.getOrElse(List.empty)} toMap

      mongoManager ! LoadDiffsIn(projectId, bugFixIdentifierList, MongoUtil.sendBackObserver[DiffModel](self))
      context.become(waitingForDiff(commitIdToBugMap))
    case msg@_ => log.error(s"Something is not right: $msg"); context.stop(self)
  }

  /**
    * Third step on the workflow: Receive the diffs and send them for change analysis
    */
  def waitingForDiff(commitIdToBugMap: Map[String, List[String]]): Receive = {
    case list: List[_] =>
      val diffList = list.asInstanceOf[List[DiffModel]]
      val identifierDiffMap = diffList.groupBy(_.identifier)
      val commitChangeAnalysisRequest = CommitChangeAnalysisRequest(identifierDiffMap)
      context.actorOf(ChangeAnalyzerAccumulator.props(projectId, Left(commitChangeAnalysisRequest), self, mongoManager,
        gitlabManager, self))
      context.become(waitingForCommitImpact(commitIdToBugMap))

    case msg@_ => log.error(s"Something is not right: $msg"); context.stop(self)
  }

  /**
    * Last step on the workflow: Receive the commit impacts calculate the [[MethodToBugAnalysis]] and return to the requester.
    */
  def waitingForCommitImpact(commitIdToBugMap: Map[String, List[String]]): Receive = {
    case list: List[_] =>
      val commitImpactList = list.asInstanceOf[List[CommitImpact]]
      val commitImpactToBugMap = commitImpactList.map((commitImpact) => commitImpact -> commitIdToBugMap(commitImpact.commitId))
      val signatureToBugMap: Map[String, List[String]] = commitImpactToBugMap flatMap { (kv) =>
        val commitImpact = kv._1
        val bugList = kv._2

        val signatures: List[String] = commitImpact.impactList.flatMap((impact) =>
          impact.methods.map(toSignature(impact)(_)))
        signatures map {_ -> bugList}
      } toMap

      requester ! MethodToBugAnalysis(signatureToBugMap)

    case msg@_ => log.error(s"Something is not right: $msg"); context.stop(self)
  }

  /**
    * Extract the signature in the format "<className>.<methodName>"  from the [[Impact]] object
    * @param impact [[Impact]] Impact object to have its siganature extracted
    * @param method [[String]] method string to be mapped
    * @return Signature in the format "<className>.<methodName>"
    */
  private def toSignature(impact: Impact)(method: String): String = {
    val fileNameWOSource = impact.fileName.drop("src/main/java/".length)
    val fileNameWOFileExtension = fileNameWOSource.split('.')(0)
    val fileNameWithDots = fileNameWOFileExtension.replace("/", ".")
    s"$fileNameWithDots.$method"
  }
}
