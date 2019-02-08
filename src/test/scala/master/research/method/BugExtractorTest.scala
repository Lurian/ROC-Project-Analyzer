package master.research.method

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import master.mock.ModelMock
import master.mongodb.commit.CommitPersistence.LoadCommitsIn
import master.mongodb.commit.{CommitImpact, CommitModel}
import master.mongodb.diff.DiffPersistence.LoadDiffsIn
import master.research.method.BugExtractor.MethodToBugAnalysis
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike, Matchers}

import scala.concurrent.duration._

class BugExtractorTest() extends TestKit(ActorSystem("BugExtractorTest")) with ImplicitSender
  with FreeSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A BugExtractor" - {
    val projectId = "1"
    val mongoManager = TestProbe()
    val requester = TestProbe()
    val gitlabManager = TestProbe()

    val bugIdList = List("1234", "1236")

    val bugFixCommit = ModelMock.commitModelMock(projectId = projectId, id = "id1", isBugFix = true, bugIds = Some(bugIdList))
    val nonBugFixCommit = ModelMock.commitModelMock(projectId = projectId, id = "id2", isBugFix = false)

    val identifierList = List(bugFixCommit.id, nonBugFixCommit.id)

    val bugExtractor = system.actorOf(BugExtractor.props(projectId, identifierList, requester.ref, mongoManager.ref, gitlabManager.ref))
    "should immediately load commit model as it starts" in {
      val receivedRequest = mongoManager.expectMsgType[LoadCommitsIn](3.seconds)
      receivedRequest.identifierList should equal(identifierList)
    }

    "then it should load the diffs only of the bug fix commits" in {
      val commitList: List[CommitModel] = List(bugFixCommit, nonBugFixCommit)
      bugExtractor ! commitList

      val receivedRequest = mongoManager.expectMsgType[LoadDiffsIn](3.seconds)
      receivedRequest.identifierList should equal(List(bugFixCommit.id))
    }

    "then it should create a ChangeAnalyzerAccumulator actor and when it receives the list of commit impact return the MethodToBugAnalysis" in {
      val diffModel = ModelMock.diffModelMock(projectId = projectId, identifier = bugFixCommit.id)

      bugExtractor ! List(diffModel)

      val impact = ModelMock.impactMock(List("method1", "method2"), fileName = "src/main/java/br/org/test/class.java")
      val commitImpact = CommitImpact(ModelMock.changeImpactMock(projectId, bugFixCommit.id, List(impact)))
      bugExtractor ! List(commitImpact)


      val expectedMap = Map.empty + ("br.org.test.class.method1" -> bugIdList) + ("br.org.test.class.method2" -> bugIdList)
      val expectedMsg = MethodToBugAnalysis(expectedMap)
      requester.expectMsg(3.seconds, expectedMsg)
    }
  }
}

