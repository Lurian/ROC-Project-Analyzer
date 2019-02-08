package master.research

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import master.mock.ModelMock
import master.mongodb.version.VersionModel
import master.mongodb.version.VersionPersistence.LoadVersions
import master.project.task.ResearchUnitManager
import master.project.task.ResearchUnitManager.{MethodResearchExtraction, VersionResearchExtraction}
import master.util.SourceApi
import org.scalamock.function.MockFunction3
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike, Matchers}

import scala.concurrent.duration._

class ResearchUnitManagerTest() extends TestKit(ActorSystem("ResearchUnitManagerTest")) with ImplicitSender
  with FreeSpecLike with Matchers with BeforeAndAfterAll with MockFactory {

  "A ResearchUnitManager" - {
    val projectId = "1"

    val mongoManager = TestProbe()
    val gitlabManager = TestProbe()
    val projectManager = TestProbe()

    val mockSourceApi = mock[SourceApi]

    val versionMakerMockFun: MockFunction3[String, List[VersionModel], ActorRef, (ActorRefFactory => ActorRef)] =
      mockFunction[String, List[VersionModel], ActorRef, (ActorRefFactory => ActorRef)]
    val methodMakerMockFun: MockFunction3[String, List[VersionModel], ActorRef, (ActorRefFactory => ActorRef)] =
      mockFunction[String, List[VersionModel], ActorRef, (ActorRefFactory => ActorRef)]

    val numberOfWorkers = 2

    val pathToProjects = "path/to/project"

    "when receiving a task for version research extraction" - {
      val researchUnitManager: ActorRef = system.actorOf(ResearchUnitManager.props(projectId, versionMakerMockFun, methodMakerMockFun,
        numberOfWorkers, mongoManager = mongoManager.ref, gitlabManager.ref, projectManager.ref, mockSourceApi))
      Thread.sleep(200)
      "should receive the request and send a load version request to the mongo manager" in {
        val versionRequest = VersionResearchExtraction(pathToProjects)
        researchUnitManager ! versionRequest

        val receivedMsg = mongoManager.expectMsgType[LoadVersions](3.seconds)
        receivedMsg.projectId should equal(projectId)
        receivedMsg.versionTagOption should equal(None)
      }

      val versionModel1 = ModelMock.versionModelMock(versionName = "v1")
      val versionModel2 = ModelMock.versionModelMock(versionName = "v2")
      "should receive the loaded versions and filter and start the VersionUnitManager" in {
        val analysisPathV1 = s"$pathToProjects/${versionModel1.versionName}"
        val analysisPathV2 = s"$pathToProjects/${versionModel2.versionName}"
        (mockSourceApi.fileExists(_: String)).expects(analysisPathV1).returns(false)
        (mockSourceApi.fileExists(_: String)).expects(analysisPathV2).returns(true)

        researchUnitManager ! List(versionModel1, versionModel2)

        versionMakerMockFun.expects(pathToProjects, List(versionModel2), researchUnitManager)
        Thread.sleep(200)
      }
    }

    "when receiving a task for method research extraction" - {
      val researchUnitManager: ActorRef = system.actorOf(ResearchUnitManager.props(projectId, versionMakerMockFun, methodMakerMockFun,
        numberOfWorkers, mongoManager = mongoManager.ref, gitlabManager.ref, projectManager.ref, mockSourceApi))
      Thread.sleep(200)
      "should receive the request and send a load version request to the mongo manager" in {
        val versionRequest = MethodResearchExtraction(pathToProjects)
        researchUnitManager ! versionRequest

        val receivedMsg = mongoManager.expectMsgType[LoadVersions](3.seconds)
        receivedMsg.projectId should equal(projectId)
        receivedMsg.versionTagOption should equal(None)
      }

      val versionModel1 = ModelMock.versionModelMock(versionName = "v1")
      val versionModel2 = ModelMock.versionModelMock(versionName = "v2")
      "should receive the loaded versions and filter and start the MethodUnitManager" in {
        val analysisPathV1 = s"$pathToProjects/${versionModel1.versionName}"
        val analysisPathV2 = s"$pathToProjects/${versionModel2.versionName}"
        (mockSourceApi.fileExists(_: String)).expects(analysisPathV1).returns(false)
        (mockSourceApi.fileExists(_: String)).expects(analysisPathV2).returns(true)

        researchUnitManager ! List(versionModel1, versionModel2)

        methodMakerMockFun.expects(pathToProjects, List(versionModel2), researchUnitManager)
        Thread.sleep(200)
      }
    }
  }
}
