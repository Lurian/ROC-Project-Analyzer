package master.project.task.change

import akka.actor.{ActorRef, Props}
import master.mongodb.ChangeImpact
import master.mongodb.commit.CommitImpact
import master.mongodb.version.VersionImpact
import master.project.task.change.ChangeAnalyzer.{CommitChangeAnalysisRequest, VersionChangeAnalysisRequest}
import master.project.task.change.ChangeWorker.{ChangeAnalysisError, ChangeAnalysisRequest}

object ChangeAnalyzerAccumulator {
  def props(projectId: String, request: Either[CommitChangeAnalysisRequest, VersionChangeAnalysisRequest], requester: ActorRef,
            mongoManager: ActorRef, gitlabManager: ActorRef, projectManager: ActorRef, routerOption: Option[ActorRef] = None): Props = {
    Props(new ChangeAnalyzerAccumulator(projectId, request, requester, mongoManager, gitlabManager, projectManager, routerOption))
  }
}

class ChangeAnalyzerAccumulator(projectId: String, request: Either[CommitChangeAnalysisRequest, VersionChangeAnalysisRequest],
                                requester: ActorRef, mongoManager: ActorRef, gitlabManager: ActorRef, projectManager: ActorRef,
                                routerOption: Option[ActorRef])
  extends ChangeAnalyzer(projectId: String, mongoManager: ActorRef, gitlabManager: ActorRef, projectManager: ActorRef, routerOption: Option[ActorRef]) {
  import ChangeAnalyzer._

  override def preStart(): Unit = {
    log.info(s"ChangeAnalyzer started with projectId:$projectId")
    request match {
      case Right(VersionChangeAnalysisRequest(versionMapDiff)) =>
        log.debug("VersionChange Analysis Request received")
        versionMapDiff foreach {(kv) => {
          val version = kv._1
          val commitDiffList = kv._2
          router ! ChangeAnalysisRequest(commitDiffList, version.versionName)
        }}
        val accumulatorMap = versionMapDiff map {_._1.versionName -> None}
        context.become(waitingForVersionImpactAccumulator(accumulatorMap))
      case Left(CommitChangeAnalysisRequest(commitMapDiff)) =>
        log.debug("CommitChange Analysis Request received")
        if(commitMapDiff.isEmpty) log.error("EMPTY MAP")

//        val setList = Set("04f15282bb357cc2663b8e74ef839ce59e604dc9", "528c218a4d2ce043716449bad2518632c9b90a11",
//          "1a295bdf0bf5a5df5f1ea05762e7843d5be0846f", "26db027045e0c3cba752b7350c0fb29df8cec68a",
//          "9404fbb9113af3b2bde5794b96e6b6555c4f9816")

//        val filtered = commitMapDiff.filter((kv) => setList.contains(kv._1))

        commitMapDiff foreach {(kv) =>
          val commitModelId = kv._1
          val commitDiffList = kv._2
          router ! ChangeAnalysisRequest(commitDiffList, commitModelId)
        }
        val accumulatorMap = commitMapDiff map {_._1 -> None}
        context.become(waitingForCommitImpactAccumulator(accumulatorMap))
    }
  }

  override def receive = waitingForImpact(Map.empty)

  def checkForCommitCompletion(newAccumulatorMap: Map[String, Option[CommitImpact]]) = {
    if(newAccumulatorMap.forall(_._2.isDefined)) {
      val commitImpactList: List[CommitImpact] = newAccumulatorMap.values.flatten.toList
      requester ! commitImpactList
      context.stop(self)
    }
  }

  def checkForVersionCompletion(newAccumulatorMap: Map[String, Option[VersionImpact]]) = {
    if(newAccumulatorMap.forall(_._2.isDefined)) {
      val versionImpactList: List[VersionImpact] = newAccumulatorMap.values.flatten.toList
      requester ! versionImpactList
      context.stop(self)
    }
  }

  def waitingForVersionImpactAccumulator(accumulatorMap: Map[String, Option[VersionImpact]]): Receive ={
    case impact@ChangeImpact(_, identifier, _) =>
      val newAccumulatorMap = accumulatorMap + (identifier -> Some(VersionImpact(impact)))
      checkForVersionCompletion(newAccumulatorMap)
      context.become(waitingForVersionImpactAccumulator(newAccumulatorMap))

    case ChangeAnalysisError(identifier, err) =>
      log.error(err.errMsg)
      log.warning(s"Error when impact analyzing identifier:$identifier, ignoring this identifier")
      val newAccumulatorMap = accumulatorMap - identifier
      checkForVersionCompletion(newAccumulatorMap)
      context.become(waitingForVersionImpactAccumulator(newAccumulatorMap))

    case GetCurrentIdentifierMap =>
      sender() ! accumulatorMap
  }

  def waitingForCommitImpactAccumulator(accumulatorMap: Map[String, Option[CommitImpact]]): Receive ={
    case impact@ChangeImpact(_, identifier, _) =>
      log.debug(s"Impact received: remaining:${accumulatorMap.values.count(_.isEmpty)}")
      val newAccumulatorMap = accumulatorMap + (identifier -> Some(CommitImpact(impact)))
      log.debug(s"${newAccumulatorMap.filter(_._2.isEmpty).keys}")
      checkForCommitCompletion(newAccumulatorMap)
      context.become(waitingForCommitImpactAccumulator(newAccumulatorMap))

    case ChangeAnalysisError(identifier, err) =>
      log.error(err.errMsg)
      log.warning(s"Error when impact analyzing identifier:$identifier, ignoring this identifier")
      val newAccumulatorMap = accumulatorMap - identifier
      checkForCommitCompletion(newAccumulatorMap)
      context.become(waitingForCommitImpactAccumulator(newAccumulatorMap))

    case GetCurrentIdentifierMap =>
      sender() ! accumulatorMap

    case msg@_ => log.error(s"Something is not right: $msg")
  }
}