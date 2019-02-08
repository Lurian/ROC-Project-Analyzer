package master.research.graph

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import master.mock.ModelMock
import master.mongodb.elastic.ElasticVersionAnalysisPersistence.LoadElasticVersionAnalysis
import master.mongodb.version.VersionPersistence.LoadVersions
import master.project.ExecUtil
import master.research.graph.GraphActor.GraphRequest
import master.research.method.BugExtractor.MethodToBugAnalysis
import master.util.{FileSource, SourceApi}
import org.scalamock.function.MockFunction2
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike, Matchers}

import scala.concurrent.duration._

class GraphActorTest() extends TestKit(ActorSystem("GraphActorTest")) with ImplicitSender
  with FreeSpecLike with Matchers with BeforeAndAfterAll with MockFactory {

  "A GraphActor" - {
    val projectId = "1"
    val requester = TestProbe()
    val mongoManager = TestProbe()
    val gitlabManager = TestProbe()
    val mockSourceApi = mock[SourceApi]
    val mockFile = stub[FileSource]
    def lines() = List(
      "M:br.gov.dpf.epol.restClass1:method1() (M)br.gov.dpf.epol.class1:method1()",
      "M:br.gov.dpf.epol.restClass1:method1() (M)br.gov.dpf.epol.class2:method2()",
      "M:br.gov.dpf.epol.restClass2:method1() (M)br.gov.dpf.epol.class1:method1()").toIterator

    val bugExtractorMakerMockFun: MockFunction2[List[String], ActorRef, (ActorRefFactory => ActorRef)] =
      mockFunction[List[String], ActorRef, (ActorRefFactory => ActorRef)]

    val graphActor = system.actorOf(GraphActor.props(projectId, requester.ref, bugExtractorMakerMockFun,
      mongoManager.ref,  gitlabManager.ref, mockSourceApi))

    "when receiving a GML graph request" - {
      val versionName = "v1"
      val graphType = GraphType.GML
      val maxRootMethod = 2
      val maxHeight = 2
      val pathToProject = "path/to/project"
      val graphRequest = GraphRequest(versionName, pathToProject, graphType, maxRootMethod, maxHeight)


      "should load the ElasticVersionAnalysis" in {
        val analysisPathV1 = s"$pathToProject/target/staticAnalysis.txt"
        (mockFile.getLines _).when().returns(lines())
        (mockSourceApi.getSource _).expects(ExecUtil.changeSlashesToFileSeparator(analysisPathV1)).returns(Some(mockFile))

        graphActor ! graphRequest

        val receivedMsg = mongoManager.expectMsgType[LoadElasticVersionAnalysis](3.seconds)
        receivedMsg.projectId should equal(projectId)
        receivedMsg.versionNameOption should equal(Some(versionName))
      }

      "then it should receive the ElasticVersionAnalysis and load the VersionModel" in {
        val restMethod1 = ModelMock.appMethodMock("br.gov.dpf.epol.restClass1", "method1()")
        val childMethod11 = ModelMock.appMethodMock("br.gov.dpf.epol.class1", "method1()")
        val childMethod22 = ModelMock.appMethodMock("br.gov.dpf.epol.class2", "method2()")
        val endpoint1 = ModelMock.endpointMock(restMethod1, usageOption = Some(100),
          impactedMethodsListOption = Some(List(childMethod11, childMethod22)))

        val restMethod2 = ModelMock.appMethodMock("br.gov.dpf.epol.restClass2", "method1()")
        val endpoint2 = ModelMock.endpointMock(restMethod2, usageOption = Some(50),
          impactedMethodsListOption = Some(List(childMethod11)))

        val elasticVersionAnalysis = ModelMock.elasticVersionAnalysisMock(projectId = projectId,
          versionName = versionName, endpointUsageList = List(endpoint1, endpoint2))

        graphActor ! List(elasticVersionAnalysis)

        val receivedMsg = mongoManager.expectMsgType[LoadVersions](3.seconds)
        receivedMsg.projectId should equal(projectId)
        receivedMsg.versionTagOption should equal(Some(versionName))
        Thread.sleep(200)
      }

      "then it should receive the VersionModel and trigger a BugExtraction" in {
        val idCommitList = List("commitId1", "commitId2", "commitId3")
        val versionModel = ModelMock.versionModelMock(projectId = projectId, versionName = versionName,
          idCommitList = idCommitList)

        bugExtractorMakerMockFun.expects(idCommitList, graphActor).returns(stubFunction[ActorRefFactory, ActorRef])

        graphActor ! List(versionModel)
        Thread.sleep(200)
      }

      "then it should receive the MethodToBugAnalysis" in {
        val map: Map[String, List[String]] = Map.empty + ("br.gov.dpf.epol.restClass1.method1" -> List("123", "124")) +
          ("br.gov.dpf.epol.class1.method1" -> List("123")) + ("br.gov.dpf.epol.restClass2.method1" -> List("123", "125"))

        val methodToBugAnalysis = MethodToBugAnalysis(map)

        graphActor ! methodToBugAnalysis
        Thread.sleep(200)
      }
    }
  }
}
