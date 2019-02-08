package master.research.method

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import master.mock.ModelMock
import master.mongodb.commit.CommitPersistence.LoadCommitsIn
import master.mongodb.coverage.CoveragePersistence.LoadCoverage
import master.mongodb.coverage.CoverageType
import master.mongodb.elastic.ElasticVersionAnalysisPersistence.LoadElasticVersionAnalysis
import master.research.method.BugExtractor.MethodToBugAnalysis
import master.util.FileSource
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike, Matchers}

import scala.concurrent.duration._

class MethodResearchUnitExtractorTest  extends TestKit(ActorSystem("MethodResearchUnitExtractorTest")) with ImplicitSender
  with FreeSpecLike with Matchers with BeforeAndAfterAll with MockFactory {

  "A MethodResearchUnitExtractor actor" - {
    val projectId = "1"
    val versionModel = ModelMock.versionModelMock(projectId = projectId)
    val staticAnalysisSource = stub[FileSource]
    def lines() = List(
      "M:br.gov.dpf.epol.restClass1:method1() (M)br.gov.dpf.epol.class1:method1()",
      "M:br.gov.dpf.epol.restClass1:method1() (M)br.gov.dpf.epol.class2:method2()",
      "M:br.gov.dpf.epol.restClass2:method1() (M)br.gov.dpf.epol.class1:method1()").toIterator

    val mongoManager = TestProbe()
    val gitlabManager = TestProbe()
    val researchManager = TestProbe()

    val mUnitExtractor = system.actorOf(MethodResearchUnitExtractor.props(projectId, versionModel, staticAnalysisSource,
      mongoManager.ref, gitlabManager.ref, researchManager.ref))

    "should load elasticVersionAnalysis on the pre-start" in {
      val receivedMsg = mongoManager.expectMsgType[LoadElasticVersionAnalysis](3.seconds)
      receivedMsg.versionNameOption should equal(Some(versionModel.versionName))
    }

    val restMethod1 = ModelMock.appMethodMock("br.gov.dpf.epol.restClass1", "method1()")
    val childMethod11 = ModelMock.appMethodMock("br.gov.dpf.epol.class1", "method1()")
    val childMethod22 = ModelMock.appMethodMock("br.gov.dpf.epol.class2", "method2()")
    val endpoint1 = ModelMock.endpointMock(restMethod1, usageOption = Some(100),
      impactedMethodsListOption = Some(List(childMethod11, childMethod22)))

    val restMethod2 = ModelMock.appMethodMock("br.gov.dpf.epol.restClass2", "method1()")
    val endpoint2 = ModelMock.endpointMock(restMethod2, usageOption = Some(50),
      impactedMethodsListOption = Some(List(childMethod11)))

    val elasticVersionAnalysis = ModelMock.elasticVersionAnalysisMock(projectId = projectId,
      endpointUsageList = List(endpoint1, endpoint2))

    "then it should receive the elasticVersionAnalysis and start a BugExtractor actor, after receiving the " +
      "methodToBugAnalysis should LoadCoverage" in {
     (staticAnalysisSource.getLines _).when().returns(lines())
      mUnitExtractor ! List(elasticVersionAnalysis)
      mongoManager.expectMsgType[LoadCommitsIn](3.seconds)

      val map: Map[String, List[String]] = Map.empty + ("br.gov.dpf.epol.restClass1.method1" -> List("123", "124")) +
        ("br.gov.dpf.epol.class1.method1" -> List("123")) + ("br.gov.dpf.epol.restClass2.method1" -> List("123", "125"))

      val methodToBugAnalysis = MethodToBugAnalysis(map)

      mUnitExtractor ! methodToBugAnalysis

      val receivedMsg = mongoManager.expectMsgType[LoadCoverage](3.seconds)
      receivedMsg.identifierOption should equal(Some(versionModel.versionName))
    }

    "then it should receive the Coverage and send the method research units back to the research manager" in {
      val instructionCounter11 = ModelMock.coverageCounterMock(CoverageType.INSTRUCTION, 50, 25, 25)
      val methodCoverage11 = ModelMock.methodCoverageMock(name = childMethod11.methodName,
        instructionCounter = instructionCounter11)

      val instructionCounter22 = ModelMock.coverageCounterMock(CoverageType.INSTRUCTION, 50, 35, 10)
      val methodCoverage22 = ModelMock.methodCoverageMock(name = childMethod22.methodName,
        instructionCounter = instructionCounter22)

      val classCoverage1 = ModelMock.classCoverageMock(childMethod11.className, methodList = List(methodCoverage11))
      val classCoverage2 = ModelMock.classCoverageMock(childMethod22.className, methodList = List(methodCoverage22))

      val instructionCounter1 = ModelMock.coverageCounterMock(CoverageType.INSTRUCTION, 50, 25, 25)
      val restMethodCoverage1 = ModelMock.methodCoverageMock(name = restMethod1.methodName,
        instructionCounter = instructionCounter1)

      val instructionCounter2 = ModelMock.coverageCounterMock(CoverageType.INSTRUCTION, 50, 35, 10)
      val restMethodCoverage2 = ModelMock.methodCoverageMock(name = restMethod2.methodName,
        instructionCounter = instructionCounter2)

      val restClassCoverage1 = ModelMock.classCoverageMock(restMethod1.className, methodList = List(restMethodCoverage1))
      val restClassCoverage2 = ModelMock.classCoverageMock(restMethod2.className, methodList = List(restMethodCoverage2))

      val packageCoverage = ModelMock.packageCoverageMock(classList = List(classCoverage1, classCoverage2,
        restClassCoverage1, restClassCoverage2))
      val instructionCounter = ModelMock.coverageCounterMock(CoverageType.INSTRUCTION, 200, 120, 80)
      val bundleCoverage = ModelMock.bundleCoverageMock(packageList = List(packageCoverage), instructionCounter = instructionCounter)
      val coverageModel = ModelMock.coverageModelMock(bundleCoverage = bundleCoverage)

      mUnitExtractor ! List(coverageModel)

      //FIXME: Endpoint not coming with usage
      val mUnit1 = MethodResearchUnit(
        methodSignature = restMethod1.signature,
        versionName = "1.0.0",
        bugQty = 2,
        timeSpentOnProduction = 0,
        totalUsage = 100,
        instructionCoverage = restMethodCoverage1.instructionCounter.coveredRatio,
        branchCoverage = restMethodCoverage1.branchCounter.coveredRatio)
      val mUnit2 = MethodResearchUnit(
        methodSignature = restMethod2.signature,
        versionName = "1.0.0",
        bugQty = 2,
        timeSpentOnProduction = 0,
        totalUsage = 50,
        instructionCoverage = restMethodCoverage2.instructionCounter.coveredRatio,
        branchCoverage = restMethodCoverage2.branchCounter.coveredRatio)
      val mUnit3 = MethodResearchUnit(
        methodSignature = childMethod11.signature,
        versionName = "1.0.0",
        bugQty = 1,
        timeSpentOnProduction = 0,
        totalUsage = 150,
        instructionCoverage = methodCoverage11.instructionCounter.coveredRatio,
        branchCoverage = methodCoverage11.branchCounter.coveredRatio)
      val mUnit4 = MethodResearchUnit(
        methodSignature = childMethod22.signature,
        versionName = "1.0.0",
        bugQty = 0,
        timeSpentOnProduction = 0,
        totalUsage = 100,
        instructionCoverage = methodCoverage22.instructionCounter.coveredRatio,
        branchCoverage = methodCoverage22.branchCounter.coveredRatio)

      val receivedMsg = researchManager.expectMsgType[List[MethodResearchUnit]](3.seconds)

      receivedMsg should contain(mUnit1)
      receivedMsg should contain(mUnit2)
      receivedMsg should contain(mUnit3)
      receivedMsg should contain(mUnit4)
    }
  }
}

