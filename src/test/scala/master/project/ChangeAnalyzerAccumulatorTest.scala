package master.project

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import master.mock.ModelMock
import master.mongodb.commit.CommitImpact
import master.mongodb.diff.DiffModel
import master.mongodb.version.{VersionImpact, VersionModel}
import master.project.task.change.ChangeAnalyzer.{CommitChangeAnalysisRequest, GetCurrentIdentifierMap, VersionChangeAnalysisRequest}
import master.project.task.change.ChangeAnalyzerAccumulator
import master.project.task.change.ChangeWorker.{ChangeAnalysisError, ChangeAnalysisRequest}
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike, Matchers}

import scala.concurrent.duration._

class ChangeAnalyzerAccumulatorTest() extends TestKit(ActorSystem("ChangeAnalyzerAccumulatorTest")) with ImplicitSender
  with FreeSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A ChangeAnalyzerAccumulator actor" - {
    val projectId = "1"
    val mongoManager = TestProbe()
    val gitlabManager = TestProbe()
    val projectManager = TestProbe()
    val router = TestProbe()

    "when doing simple CommitAnalysis" - {
      val commitId: String = "1.0.0"
      val diffModel: DiffModel = ModelMock.diffModelMock()
      val map: Map[String, List[DiffModel]] = Map.empty + (commitId -> List(diffModel))
      val commitChangeReq = CommitChangeAnalysisRequest(map)
      val requester = TestProbe()

      val changeAnalyzer = system.actorOf(ChangeAnalyzerAccumulator.props(projectId, Left(commitChangeReq), requester.ref,
        mongoManager.ref, gitlabManager.ref, projectManager.ref, Some(router.ref)))
      "must send a ChangeAnalysisRequest to router on pre-start, before receiving any message" in {
        val expectedRouterMsg = ChangeAnalysisRequest(List(diffModel), commitId)
        router.expectMsg(3.seconds, expectedRouterMsg)

        changeAnalyzer ! GetCurrentIdentifierMap
        val expectedIdentifierMap = Map.empty + (commitId -> None)
        expectMsg(expectedIdentifierMap)
      }

      "then it must send a List of commit impacts to the requester after receiving the processed impact (which is the last one)" in {
        val impactMock = ModelMock.impactMock()
        val commitChangeImpact = ModelMock.changeImpactMock(identifier = commitId, impactList = List(impactMock))
        val commitImpact = CommitImpact(commitChangeImpact)
        this watch changeAnalyzer

        changeAnalyzer ! commitChangeImpact
        requester.expectMsg(3.seconds, List(commitImpact))
        expectTerminated(changeAnalyzer)
      }
    }

    "when doing simple VersionAnalysis" - {
      val versionModel: VersionModel = ModelMock.versionModelMock()
      val diffModel: DiffModel = ModelMock.diffModelMock()
      val map: Map[VersionModel, List[DiffModel]] = Map.empty + (versionModel -> List(diffModel))
      val versionChangeReq = VersionChangeAnalysisRequest(map)
      val requester = TestProbe()

      val changeAnalyzer = system.actorOf(ChangeAnalyzerAccumulator.props(projectId, Right(versionChangeReq), requester.ref,
        mongoManager.ref, gitlabManager.ref, projectManager.ref, Some(router.ref)))
      "must send a ChangeAnalysisRequest to router after receiving an CommitChangeAnalysisRequest" in {
        val expectedRouterMsg = ChangeAnalysisRequest(List(diffModel), versionModel.versionName)
        router.expectMsg(3.seconds, expectedRouterMsg)

        changeAnalyzer ! GetCurrentIdentifierMap
        val expectedIdentifierMap = Map.empty + (versionModel.versionName -> None)
        expectMsg(expectedIdentifierMap)
      }

      "then it must send a PersistCommitImpact to mongoManager after receiving the processed impact" in {
        val impactMock = ModelMock.impactMock()
        val versionChangeImpact = ModelMock.changeImpactMock(identifier = versionModel.versionName, impactList = List(impactMock))
        val versionImpact = VersionImpact(versionChangeImpact)
        this watch changeAnalyzer

        changeAnalyzer ! versionChangeImpact
        requester.expectMsg(3.seconds, List(versionImpact))
        expectTerminated(changeAnalyzer)
      }
    }

    "when doing advanced CommitAnalysis" - {
      val commitId1: String = "id1"
      val commitId2: String = "id2"
      val commitId3: String = "id3"
      val diffModel1: DiffModel = ModelMock.diffModelMock(identifier = commitId1)
      val diffModel2: DiffModel = ModelMock.diffModelMock(identifier = commitId2)
      val diffModel3: DiffModel = ModelMock.diffModelMock(identifier = commitId3)

      val commitMapDiff: Map[String, List[DiffModel]] = Map.empty + (commitId1 -> List(diffModel1)) +
        (commitId2 -> List(diffModel2)) + (commitId3 -> List(diffModel3))
      val commitChangeReq = CommitChangeAnalysisRequest(commitMapDiff)
      val requester = TestProbe()

      val changeAnalyzer = system.actorOf(ChangeAnalyzerAccumulator.props(projectId, Left(commitChangeReq),
        requester.ref, mongoManager.ref, gitlabManager.ref, projectManager.ref, Some(router.ref)))
      val expectedInitialIdentifierMap: Map[String, Option[CommitImpact]] = commitMapDiff map {_._1 -> None}
      "must send a ChangeAnalysisRequest to router for each CommitModel/DiffModel key/par received on pre-start, before receiving any message" in {
        val expectedRouterMsgs: Seq[ChangeAnalysisRequest] = commitMapDiff.map({(kv) => ChangeAnalysisRequest(kv._2, kv._1)}).toSeq
        router.expectMsgAllOf[ChangeAnalysisRequest](3.seconds, expectedRouterMsgs:_*)

        changeAnalyzer ! GetCurrentIdentifierMap
        expectMsg(expectedInitialIdentifierMap)
      }

      val impactMock1 = ModelMock.impactMock()
      val commitChangeImpact1 = ModelMock.changeImpactMock(identifier = commitId1, impactList = List(impactMock1))
      val commitImpact1 = CommitImpact(commitChangeImpact1)
      "then when it receives a ChangeImpact message it should put on the accumulator map and not send messages to requester until completion" in {
        changeAnalyzer ! commitChangeImpact1
        requester.expectNoMessage(3.seconds)

        changeAnalyzer ! GetCurrentIdentifierMap
        val expectedIdentifierMap = expectedInitialIdentifierMap + (commitId1 -> Some(commitImpact1))
        expectMsg(expectedIdentifierMap)
      }

      "then when it receives a ChangeAnalysisError message it should remove the identifier of the accumulator map and not send messages to requester until completion" in {
        val changeAnalysisError: ChangeAnalysisError = ChangeAnalysisError(commitId2, ModelMock.gitlabErrorMock())

        changeAnalyzer ! changeAnalysisError
        requester.expectNoMessage(3.seconds)

        changeAnalyzer ! GetCurrentIdentifierMap
        val expectedIdentifierMap = expectedInitialIdentifierMap + (commitId1 -> Some(commitImpact1)) - commitId2
        expectMsg(expectedIdentifierMap)
      }

      "then when it receives the last ChangeImpact message it should check for completion and send the CommitImpacts to the requester" in {
        val impactMock3 = ModelMock.impactMock()
        val commitChangeImpact3 = ModelMock.changeImpactMock(identifier = commitId3, impactList = List(impactMock3))
        val commitImpact3 = CommitImpact(commitChangeImpact3)

        this watch changeAnalyzer
        changeAnalyzer ! commitChangeImpact3
        requester.expectMsg(3.seconds, List(commitImpact1, commitImpact3))
        expectTerminated(changeAnalyzer)
      }
    }
  }
}