package master.research.method

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem, PoisonPill}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import master.mock.ModelMock
import master.mongodb.version.VersionModel
import master.project.ExecUtil
import master.project.task.ResearchUnitManager.{GetCurrentWorkerMap, GetDoneList, GetRemainingList}
import master.research.version.VersionUnitManager._
import master.util.{FileSource, SourceApi}
import org.scalamock.function.MockFunction1
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike, Matchers}

import scala.concurrent.duration._

class MethodUnitManagerTest() extends TestKit(ActorSystem("MethodUnitManagerTest")) with ImplicitSender
  with FreeSpecLike with Matchers with BeforeAndAfterAll with MockFactory {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A MethodUnitManager" - {
    val projectId = "1"

    val mongoManager = TestProbe()
    val gitlabManager = TestProbe()
    val projectManager = TestProbe()

    val mockFactoryFunction: MockFunction1[ActorRefFactory, ActorRef] = mockFunction[ActorRefFactory, ActorRef]
    val childMakerFun = (_: VersionModel, _: FileSource, _: ActorRef) => mockFactoryFunction

    val pathToProjects = "path/to/project"
    val mockSourceApi = mock[SourceApi]

    val versionModel1 = ModelMock.versionModelMock(versionName = "v1")
    val versionModel2 = ModelMock.versionModelMock(versionName = "v2")
    val childMock1 = TestProbe()
    val childMock2 = TestProbe()

    val versionModel3 = ModelMock.versionModelMock(versionName = "v3")
    val versionModel4 = ModelMock.versionModelMock(versionName = "v4")

    val numberOfWorkers = 2
    var methodUnitManager: ActorRef = null
    val mockFile = stub[FileSource]
    "should get the FileSources and instantiate two child workers" in {
      val analysisPathV1 = s"$pathToProjects/${versionModel1.versionName}/target/staticAnalysis.txt"
      (mockSourceApi.getSource _).expects(ExecUtil.changeSlashesToFileSeparator(analysisPathV1)).returns(Some(mockFile))
      val analysisPathV2 = s"$pathToProjects/${versionModel2.versionName}/target/staticAnalysis.txt"
      (mockSourceApi.getSource _).expects(ExecUtil.changeSlashesToFileSeparator(analysisPathV2)).returns(Some(mockFile))

      mockFactoryFunction.expects(*).returning(childMock1.ref)
      mockFactoryFunction.expects(*).returning(childMock2.ref)

      val versionList = List(versionModel1, versionModel2, versionModel3, versionModel4)
      methodUnitManager = system.actorOf(MethodUnitManager.props(projectId, pathToProjects, versionList,
        childMakerFun, numberOfWorkers, mockSourceApi, mongoManager.ref, gitlabManager.ref, projectManager.ref))
      Thread.sleep(200)
    }

    "should have the two child workers in its worker map" in {
      val expectedMap = Map.empty + (versionModel1.versionName -> childMock1.ref) + (versionModel2.versionName -> childMock2.ref)
      methodUnitManager ! GetCurrentWorkerMap()
      expectMsg(3.seconds, expectedMap)
    }

    "should have an empty done list" in {
      val expectedList = List.empty
      methodUnitManager ! GetDoneList()
      expectMsg(3.seconds, expectedList)
    }

    "should have the two versions in the remaining list" in {
      val expectedList = List(versionModel3, versionModel4)
      methodUnitManager ! GetRemainingList()
      expectMsg(3.seconds, expectedList)
    }

    val childMock3 = TestProbe()
    val MRU11: MethodResearchUnit = ModelMock.methodResearchUnitMock(methodSignature = "class.method11()",
      versionName = versionModel1.versionName)
    val MRU12: MethodResearchUnit = ModelMock.methodResearchUnitMock(methodSignature = "class.method12()",
      versionName = versionModel1.versionName)
    "should receive a MethodResearchUnit and trigger another version extraction from the remaining list" in {
      val analysisPathV3 = s"$pathToProjects/${versionModel3.versionName}/target/staticAnalysis.txt"
      (mockSourceApi.getSource _).expects(ExecUtil.changeSlashesToFileSeparator(analysisPathV3)).returns(Some(mockFile))
      mockFactoryFunction.expects(*).returning(childMock3.ref)

      methodUnitManager ! List(MRU11, MRU12)

      Thread.sleep(200)
    }

    "should still have two child workers in its worker map but the first should have been swapped with the third" in {
      val expectedMap = Map.empty + (versionModel3.versionName -> childMock3.ref) + (versionModel2.versionName -> childMock2.ref)
      methodUnitManager ! GetCurrentWorkerMap()
      expectMsg(3.seconds, expectedMap)
    }

    "should have a done list with the MRU11 and MRU12" in {
      val expectedList = List(MRU11, MRU12)
      methodUnitManager ! GetDoneList()
      expectMsg(3.seconds, expectedList)
    }

    "should have the one version in the remaining list" in {
      val expectedList = List(versionModel4)
      methodUnitManager ! GetRemainingList()
      expectMsg(3.seconds, expectedList)
    }

    val childMock4 = TestProbe()
    "should receive a Terminated message and trigger another version extraction from the remaining list" in {
      val analysisPathV4 = s"$pathToProjects/${versionModel4.versionName}/target/staticAnalysis.txt"
      (mockSourceApi.getSource _).expects(ExecUtil.changeSlashesToFileSeparator(analysisPathV4)).returns(Some(mockFile))
      mockFactoryFunction.expects(*).returning(childMock4.ref)

      childMock2.ref ! PoisonPill

      Thread.sleep(200)
    }

    "should still have two child workers in its worker map but the second should have been swapped with the fourth" in {
      val expectedMap = Map.empty + (versionModel3.versionName -> childMock3.ref) + (versionModel4.versionName -> childMock4.ref)
      methodUnitManager ! GetCurrentWorkerMap()
      expectMsg(3.seconds, expectedMap)
    }

    "should still have a done list with only the MRU11 and MRU12" in {
      val expectedList = List(MRU11, MRU12)
      methodUnitManager ! GetDoneList()
      expectMsg(3.seconds, expectedList)
    }

    "should have an empty remaining list" in {
      val expectedList = List.empty
      methodUnitManager ! GetRemainingList()
      expectMsg(3.seconds, expectedList)
    }

    val MRU31 = ModelMock.methodResearchUnitMock(methodSignature = "class.method31()",
      versionName = versionModel3.versionName)
    "should receive a MethodResearchUnit and update its internal state correctly" in {
      methodUnitManager ! List(MRU31)
      Thread.sleep(200)

      val expectedWorkerMap = Map.empty + (versionModel4.versionName -> childMock4.ref)
      methodUnitManager ! GetCurrentWorkerMap()
      expectMsg(3.seconds, expectedWorkerMap)

      val expectedDoneList = List(MRU11, MRU12, MRU31)
      methodUnitManager ! GetDoneList()
      expectMsg(3.seconds, expectedDoneList)

      val expectedRemainingList = List.empty
      methodUnitManager ! GetRemainingList()
      expectMsg(3.seconds, expectedRemainingList)
    }

    val MRU41 = ModelMock.methodResearchUnitMock(methodSignature = "class.method41()",
      versionName = versionModel4.versionName)
    val MRU42 = ModelMock.methodResearchUnitMock(methodSignature = "class.method42()",
      versionName = versionModel4.versionName)
    val MRU43 = ModelMock.methodResearchUnitMock(methodSignature = "class.method43()",
      versionName = versionModel4.versionName)
    "should receive the last MethodResearchUnit and write it to csv format" in {
      val expectedList = List(MRU11, MRU12, MRU31, MRU41, MRU42, MRU43)
      (mockSourceApi.exportToCsv _).expects(expectedList, "./target/methodUnits.csv")
      methodUnitManager ! List(MRU41, MRU42, MRU43)
      Thread.sleep(200)
    }
  }
}
