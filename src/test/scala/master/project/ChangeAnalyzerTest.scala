package master.project

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import master.mock.ModelMock
import master.mongodb.commit.CommitImpact
import master.mongodb.commit.CommitImpactPersistence.PersistCommitImpact
import master.mongodb.diff.DiffModel
import master.mongodb.version.VersionImpactPersistence.PersistVersionImpact
import master.mongodb.version.{VersionImpact, VersionModel}
import master.project.task.change.ChangeAnalyzer
import master.project.task.change.ChangeAnalyzer.{CommitChangeAnalysisRequest, GetCurrentIdentifierMap, VersionChangeAnalysisRequest}
import master.project.task.change.ChangeWorker.ChangeAnalysisRequest
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike, Matchers}

import scala.concurrent.duration._

class ChangeAnalyzerTest() extends TestKit(ActorSystem("ChangeAnalyzerTest")) with ImplicitSender
  with FreeSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A ChangeAnalyzer actor" - {
    val projectId = "1"
    val mongoManager = TestProbe()
    val gitlabManager = TestProbe()
    val projectManager = TestProbe()
    val router = TestProbe()

    "when doing CommitAnalysis" - {
      val changeAnalyzer = system.actorOf(ChangeAnalyzer.props(projectId, mongoManager.ref, gitlabManager.ref,
        projectManager.ref, Some(router.ref)))
      val commitId: String = "commitId"
      val diffModel: DiffModel = ModelMock.diffModelMock()

      "must send a ChangeAnalysisRequest to router after receiving an CommitChangeAnalysisRequest" in {
        val map: Map[String, List[DiffModel]] = Map.empty + (commitId -> List(diffModel))
        val commitChangeReq = CommitChangeAnalysisRequest(map)
        val expectedRouterMsg = ChangeAnalysisRequest(List(diffModel), commitId)

        changeAnalyzer ! commitChangeReq
        router.expectMsg(3.seconds, expectedRouterMsg)

        changeAnalyzer ! GetCurrentIdentifierMap
        val expectedIdentifierMap = Map.empty + (commitId -> Left(CommitImpact))
        expectMsg(expectedIdentifierMap)
      }

      "then it must send a PersistCommitImpact to mongoManager after receiving the processed impact" in {
        val commitChangeImpact = ModelMock.changeImpactMock(identifier = commitId)

        changeAnalyzer ! commitChangeImpact
        mongoManager.expectMsgType[PersistCommitImpact](3.seconds)

        changeAnalyzer ! GetCurrentIdentifierMap
        val expectedIdentifierMap = Map.empty
        expectMsg(expectedIdentifierMap)
      }
    }

    "when doing VersionAnalysis" - {
      val changeAnalyzer = system.actorOf(ChangeAnalyzer.props(projectId, mongoManager.ref, gitlabManager.ref,
        projectManager.ref, Some(router.ref)))
      val versionModel: VersionModel = ModelMock.versionModelMock()
      val diffModel: DiffModel = ModelMock.diffModelMock()

      "must send a ChangeAnalysisRequest to router after receiving an VersionChangeAnalysisRequest" in {
        val map: Map[VersionModel, List[DiffModel]] = Map.empty + (versionModel -> List(diffModel))
        val commitChangeReq = VersionChangeAnalysisRequest(map)
        val expectedRouterMsg = ChangeAnalysisRequest(List(diffModel), versionModel.versionName)

        changeAnalyzer ! commitChangeReq
        router.expectMsg(3.seconds, expectedRouterMsg)

        changeAnalyzer ! GetCurrentIdentifierMap
        val expectedIdentifierMap = Map.empty + (versionModel.versionName -> Right(VersionImpact))
        expectMsg(expectedIdentifierMap)
      }

      "then it must send a PersistVersionImpact to mongoManager after receiving the processed impact" in {
        val versionChangeImpact = ModelMock.changeImpactMock(identifier = versionModel.versionName)

        changeAnalyzer ! versionChangeImpact
        mongoManager.expectMsgType[PersistVersionImpact](3.seconds)

        changeAnalyzer ! GetCurrentIdentifierMap
        val expectedIdentifierMap = Map.empty
        expectMsg(expectedIdentifierMap)
      }
    }
  }
}