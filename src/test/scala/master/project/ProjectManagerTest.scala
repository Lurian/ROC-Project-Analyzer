package master.project

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import master.mock.BugFileMock
import master.mongodb.bug.BugPersistence.PersistBugs
import master.project.ProjectActorFactory.ProjectActorFactoryConfig
import master.project.ProjectManager.{FullLoadTask, ManagerConfig}
import master.project.task.BugAnalysisTaskManager.{BugAnalysis, BugAnalysisTaskCompleted}
import master.project.task.ChangeAnalysisTaskManager.{CommitChangeAnalysis, VersionChangeAnalysis}
import master.project.task.CoverageAnalysisTaskManager.{CoverageAnalysisCompleted, CoverageAnalysisRequest}
import master.project.task.ElasticTaskAnalyzer.ElasticAnalysisCompleted
import master.project.task.ElasticTaskManager.ElasticAnalysisTask
import master.project.task.EndpointAnalysisTaskManager.{EndpointAnalysisTaskCompleted, EndpointAnalysisTaskRequest}
import master.project.task.InitDataTaskManager.{InitDataLoadTaskCompleted, LoadInitData}
import master.project.task.LogAnalysisTaskManager.LogAnalysis
import master.project.task.ResearchUnitManager.{MethodResearchExtraction, VersionResearchExtraction}
import master.project.task.VersionAnalysisTaskManager.{VersionAnalysis, VersionAnalysisTaskCompleted}
import master.project.task.change.ChangeAnalyzer.VersionChangeAnalysisTaskCompleted
import master.util.{FileSource, SourceApi}
import org.scalamock.function.{StubFunction1, StubFunction5}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike, Matchers}

import scala.concurrent.duration._

class ProjectManagerTest() extends TestKit(ActorSystem("ProjectManagerTest")) with ImplicitSender
  with FreeSpecLike with Matchers with BeforeAndAfterAll with MockFactory {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  trait ProjectManagerFixture {
    val pathToProject = "path/to/project"

    val bugAnalysisManagerFactory: StubFunction1[ActorRefFactory, ActorRef] = stubFunction[ActorRefFactory, ActorRef]
    val bugAnalysisManager = TestProbe()

    val changeAnalysisManagerFactory: StubFunction1[ActorRefFactory, ActorRef] = stubFunction[ActorRefFactory, ActorRef]
    val changeAnalysisManager = TestProbe()

    val versionAnalysisManagerFactory: StubFunction1[ActorRefFactory, ActorRef] = stubFunction[ActorRefFactory, ActorRef]
    val versionAnalysisManager = TestProbe()

    val initLoadManagerFactory: StubFunction1[ActorRefFactory, ActorRef] = stubFunction[ActorRefFactory, ActorRef]
    val initLoadManager = TestProbe()

    val coverageAnalysisManagerFactory: StubFunction1[ActorRefFactory, ActorRef] = stubFunction[ActorRefFactory, ActorRef]
    val coverageAnalysisManager = TestProbe()

    val logAnalyzerManagerFactory: StubFunction1[ActorRefFactory, ActorRef] = stubFunction[ActorRefFactory, ActorRef]
    val logAnalyzerManager = TestProbe()

    val endpointAnalyzerManagerFactory: StubFunction1[ActorRefFactory, ActorRef] = stubFunction[ActorRefFactory, ActorRef]
    val endpointAnalyzerManager = TestProbe()

    val elasticManagerFactory: StubFunction1[ActorRefFactory, ActorRef] = stubFunction[ActorRefFactory, ActorRef]
    val elasticManager = TestProbe()

    val researchManagerFactory: StubFunction1[ActorRefFactory, ActorRef] = stubFunction[ActorRefFactory, ActorRef]
    val researchManager = TestProbe()
    val actorRefFactory: ActorRefFactory = stub[ActorRefFactory]
    val projectActorConfigMock = ProjectActorFactoryConfig(actorRefFactory, bugAnalysisManagerFactory,
      changeAnalysisManagerFactory, versionAnalysisManagerFactory, initLoadManagerFactory, coverageAnalysisManagerFactory, logAnalyzerManagerFactory,
      endpointAnalyzerManagerFactory, elasticManagerFactory, researchManagerFactory)

    val projectActorConfig: ProjectActorConfig = stub[ProjectActorConfig]
    (projectActorConfig.createFactoryConfig _).when(*, *, *, *, *, *, *, *, *, *).returns(projectActorConfigMock)

    val mockProjectActorConfigGenerator: StubFunction5[ManagerConfig, String, ActorRef, ActorRef, ActorRef, ProjectActorConfig] =
      stubFunction[ManagerConfig, String, ActorRef, ActorRef, ActorRef, ProjectActorConfig]
    val projectConfig: ManagerConfig = stub[ManagerConfig]
    val projectId = "1"
    val mongoManager = TestProbe()
    val gitlabManager = TestProbe()
    val mockSourceApi = mock[SourceApi]

    researchManagerFactory.when(*).returns(researchManager.ref)
    bugAnalysisManagerFactory.when(*).returns(bugAnalysisManager.ref)
    changeAnalysisManagerFactory.when(*).returns(changeAnalysisManager.ref)
    versionAnalysisManagerFactory.when(*).returns(versionAnalysisManager.ref)
    initLoadManagerFactory.when(*).returns(initLoadManager.ref)
    coverageAnalysisManagerFactory.when(*).returns(coverageAnalysisManager.ref)
    logAnalyzerManagerFactory.when(*).returns(logAnalyzerManager.ref)
    endpointAnalyzerManagerFactory.when(*).returns(endpointAnalyzerManager.ref)
    elasticManagerFactory.when(*).returns(elasticManager.ref)
    mockProjectActorConfigGenerator.when(*, *, *, *, *).returns(projectActorConfig)

    val projectManager: ActorRef = system.actorOf(ProjectManager.props(projectConfig, projectId,
      mongoManager.ref, gitlabManager.ref, mockSourceApi, mockProjectActorConfigGenerator))
    Thread.sleep(200)
  }

  "A ProjectManager actor" - {
    "when doing a full load" - new ProjectManagerFixture {
      "should start by sending a LoadInitData to the InitLoadManager" in {
        projectManager ! FullLoadTask()
        initLoadManager.expectMsgType[LoadInitData](3.seconds)
      }

      "should do a VersionAnalysis when it receives a InitDataLoadTaskCompleted flag" in {
        projectManager ! InitDataLoadTaskCompleted()
        versionAnalysisManager.expectMsgType[VersionAnalysis](3.seconds)
      }

      "should do a BugAnalysis when it receives a VersionAnalysisTaskCompleted flag" in {
        val mockSourceFile = mock[FileSource]
        (mockSourceFile.mkString _).expects().returns(BugFileMock.getSimpleBugFile())
        (mockSourceApi.getSource(_: String)).expects("bugs.json").returns(Some(mockSourceFile))

        projectManager ! VersionAnalysisTaskCompleted()
        val resultMessage = mongoManager.expectMsgType[PersistBugs](3.seconds)

        // Calling the callback of the persistence request to check if BugAnalysis is the next task to be done.
        resultMessage.callback()
        bugAnalysisManager.expectMsgType[BugAnalysis](3.seconds)
      }

      "should do a VersionChangeAnalysis when it receives a BugAnalysisTaskCompleted flag" in {
        projectManager ! BugAnalysisTaskCompleted()
        changeAnalysisManager.expectMsgType[VersionChangeAnalysis](3.seconds)
      }

      "should do a EndpointAnalysis when it receives a VersionChangeAnalysisTaskCompleted flag" in {
        projectManager ! VersionChangeAnalysisTaskCompleted()
        endpointAnalyzerManager.expectMsgType[EndpointAnalysisTaskRequest](3.seconds)
      }

      "should do a ElasticAnalysis when it receives a EndpointAnalysisTaskCompleted flag" in {
        projectManager ! EndpointAnalysisTaskCompleted()
        elasticManager.expectMsgType[ElasticAnalysisTask](3.seconds)
      }

      "should do a CoverageAnalysis when it receives a ElasticAnalysisCompleted flag" in {
        projectManager ! ElasticAnalysisCompleted()
        coverageAnalysisManager.expectMsgType[CoverageAnalysisRequest](3.seconds)
      }

      "should do a VersionResearchExtraction when it receives a CoverageAnalysisCompleted flag" in {
        projectManager ! CoverageAnalysisCompleted()
        versionAnalysisManager.expectMsgType[VersionResearchExtraction](3.seconds)
      }
    }

    "when waiting for tasks" - {
      "should forward a LoadInitData message to the InitLoadManager " in new ProjectManagerFixture {
        projectManager ! LoadInitData()
        initLoadManager.expectMsgType[LoadInitData](3.seconds)
      }

      "should forward a VersionAnalysis message to the VersionAnalysisManager " in new ProjectManagerFixture {
        projectManager ! VersionAnalysis()
        versionAnalysisManager.expectMsgType[VersionAnalysis](3.seconds)
      }

      "should forward a BugAnalysis message to the BugAnalysisManager " in new ProjectManagerFixture {
        projectManager ! BugAnalysis()
        bugAnalysisManager.expectMsgType[BugAnalysis](3.seconds)
      }

      "should forward a CommitChangeAnalysis message to the ChangeAnalysisManager " in new ProjectManagerFixture {
        projectManager ! CommitChangeAnalysis()
        changeAnalysisManager.expectMsgType[CommitChangeAnalysis](3.seconds)
      }

      "should forward a VersionChangeAnalysis message to the ChangeAnalysisManager " in new ProjectManagerFixture {
        projectManager ! VersionChangeAnalysis()
        changeAnalysisManager.expectMsgType[VersionChangeAnalysis](3.seconds)
      }

      "should forward a CoverageAnalysisRequest message to the CoverageAnalysisManager " in new ProjectManagerFixture {
        projectManager ! CoverageAnalysisRequest(pathToProject)
        coverageAnalysisManager.expectMsgType[CoverageAnalysisRequest](3.seconds)
      }

      "should forward a LogAnalysis message to the LogAnalyzerManager " in new ProjectManagerFixture {
        projectManager ! LogAnalysis(pathToProject)
        logAnalyzerManager.expectMsgType[LogAnalysis](3.seconds)
      }

      "should forward a EndpointAnalysisTaskRequest message to the EndpointAnalyzerManager " in new ProjectManagerFixture {
        projectManager ! EndpointAnalysisTaskRequest(pathToProject)
        endpointAnalyzerManager.expectMsgType[EndpointAnalysisTaskRequest](3.seconds)
      }

      "should forward a ElasticAnalysisTask message to the ElasticManager " in new ProjectManagerFixture {
        projectManager ! ElasticAnalysisTask()
        elasticManager.expectMsgType[ElasticAnalysisTask](3.seconds)
      }

      "should forward a VersionResearchExtraction message to the ResearchManager " in new ProjectManagerFixture {
        projectManager ! VersionResearchExtraction(pathToProject)
        researchManager.expectMsgType[VersionResearchExtraction](3.seconds)
      }

      "should forward a MethodResearchExtraction message to the ResearchManager " in new ProjectManagerFixture {
        projectManager ! MethodResearchExtraction(pathToProject)
        researchManager.expectMsgType[MethodResearchExtraction](3.seconds)
      }
    }
  }
}
