package master.research.version

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import master.mock.ModelMock
import master.mongodb.coverage.CoveragePersistence.LoadCoverage
import master.mongodb.coverage.CoverageType
import master.mongodb.elastic.ElasticVersionAnalysisPersistence.LoadElasticVersionAnalysis
import master.util.{FileSource, SourceApi}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike, Matchers}

import scala.concurrent.duration._

class VersionResearchUnitExtractorTest() extends TestKit(ActorSystem("VersionResearchUnitExtractorTest")) with ImplicitSender
  with FreeSpecLike with Matchers with BeforeAndAfterAll with MockFactory {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "A VersionResearchUnitExtractor" - {
    val mongoManager = TestProbe()
    val researchManager = TestProbe()
    val projectId = "1"
    val versionModel = ModelMock.versionModelMock(versionName = "1.10.0", bugCount = 2)
    val staticAnalysisSource = stub[FileSource]
    val mockSourceApi = mock[SourceApi]

    def lines() = List(
      "M:br.gov.dpf.epol.restClass1:method1() (M)br.gov.dpf.epol.class1:method1()",
      "M:br.gov.dpf.epol.restClass1:method1() (M)br.gov.dpf.epol.class2:method2()",
      "M:br.gov.dpf.epol.restClass2:method1() (M)br.gov.dpf.epol.class1:method1()").toIterator
    val versionResearchUnitExtractor = system.actorOf(VersionResearchUnitExtractor.props(projectId, versionModel,
      staticAnalysisSource, mongoManager.ref, researchManager.ref, mockSourceApi))

    "should request a ElasticVersion load on the preStart" in {
      val receivedMsg = mongoManager.expectMsgType[LoadElasticVersionAnalysis](3.seconds)
      receivedMsg.versionNameOption should equal(Some(versionModel.versionName))
    }

    val restMethod1 = ModelMock.appMethodMock("br.gov.dpf.epol.restClass1", "method1()")
    val childMethod11 = ModelMock.appMethodMock("br.gov.dpf.epol.class1", "method1()")
    val childMethod22 = ModelMock.appMethodMock("br.gov.dpf.epol.class2", "method2()")
    val endpoint1Usage = 100
    val endpoint1 = ModelMock.endpointMock(restMethod1, usageOption = Some(endpoint1Usage),
      impactedMethodsListOption = Some(List(childMethod11, childMethod22)))

    val restMethod2 = ModelMock.appMethodMock("br.gov.dpf.epol.restClass2", "method1()")
    val endpoint2Usage = 50
    val endpoint2 = ModelMock.endpointMock(restMethod2, usageOption = Some(endpoint2Usage),
      impactedMethodsListOption = Some(List(childMethod11)))

    val elasticVersionAnalysis = ModelMock.elasticVersionAnalysisMock(projectId = projectId,
      endpointUsageList = List(endpoint1, endpoint2))

    "then when it receives the ElasticVersionAnalysis it should calculate a signatureToUsageMap and request coverage load" in {
      (staticAnalysisSource.getLines _).when().returns(lines())
      versionResearchUnitExtractor ! List(elasticVersionAnalysis)

      val receivedMsg = mongoManager.expectMsgType[LoadCoverage](3.seconds)
      receivedMsg.identifierOption should equal(Some(versionModel.versionName))
    }

    "then when it receives the coverage load it should calculate the VersionResearchUnits and return them to the researchManager" in {
      // Setup
      val instructionCounter11 = ModelMock.coverageCounterMock(CoverageType.INSTRUCTION, 50, 25, 25)
      val methodCoverage11 = ModelMock.methodCoverageMock(name = childMethod11.methodName,
        instructionCounter = instructionCounter11)
      val branchCounter11 = methodCoverage11.branchCounter

      val instructionCounter22 = ModelMock.coverageCounterMock(CoverageType.INSTRUCTION, 50, 35, 10)
      val methodCoverage22 = ModelMock.methodCoverageMock(name = childMethod22.methodName,
        instructionCounter = instructionCounter22)
      val branchCounter22 = methodCoverage22.branchCounter

      val classCoverage1 = ModelMock.classCoverageMock(childMethod11.classDeclaration, methodList = List(methodCoverage11))
      val classCoverage2 = ModelMock.classCoverageMock(childMethod22.classDeclaration, methodList = List(methodCoverage22))

      val instructionCounter1 = ModelMock.coverageCounterMock(CoverageType.INSTRUCTION, 50, 25, 25)
      val restMethodCoverage1 = ModelMock.methodCoverageMock(name = restMethod1.methodName,
        instructionCounter = instructionCounter1)
      val branchCounter1 = restMethodCoverage1.branchCounter

      val instructionCounter2 = ModelMock.coverageCounterMock(CoverageType.INSTRUCTION, 50, 35, 10)
      val restMethodCoverage2 = ModelMock.methodCoverageMock(name = restMethod2.methodName,
        instructionCounter = instructionCounter2)
      val branchCounter2 = restMethodCoverage2.branchCounter

      val restClassCoverage1 = ModelMock.classCoverageMock(restMethod1.classDeclaration, methodList = List(restMethodCoverage1))
      val restClassCoverage2 = ModelMock.classCoverageMock(restMethod2.classDeclaration, methodList = List(restMethodCoverage2))

      val packageCoverage = ModelMock.packageCoverageMock(classList = List(classCoverage1, classCoverage2,
        restClassCoverage1, restClassCoverage2))
      val instructionCounter = ModelMock.coverageCounterMock(CoverageType.INSTRUCTION, 200, 120, 80)
      val bundleCoverage = ModelMock.bundleCoverageMock(packageList = List(packageCoverage), instructionCounter = instructionCounter)
      val coverageModel = ModelMock.coverageModelMock(bundleCoverage = bundleCoverage)

      //Execution
      versionResearchUnitExtractor ! List(coverageModel)

      //Assertion
      val child1Usage: Double = endpoint1Usage + endpoint2Usage
      val child2Usage: Double = endpoint1Usage
      val average: Double = (endpoint1Usage + endpoint2Usage + child1Usage + child2Usage) / 4

      val modifiedInstCoveredCounter11: Int = (instructionCounter11.coveredCount * VersionResearchUnitExtractor.calculateDistanceToAverageUsage(average)(child1Usage)).toInt
      val modifiedInstCoveredCounter21: Int = (instructionCounter22.coveredCount * VersionResearchUnitExtractor.calculateDistanceToAverageUsage(average)(child2Usage)).toInt
      val modifiedInstCoveredCounter1: Int = (instructionCounter1.coveredCount * VersionResearchUnitExtractor.calculateDistanceToAverageUsage(average)(endpoint1Usage)).toInt
      val modifiedInstCoveredCounter2: Int = (instructionCounter2.coveredCount * VersionResearchUnitExtractor.calculateDistanceToAverageUsage(average)(endpoint2Usage)).toInt

      val modifiedInstTotalCounter11: Int = (instructionCounter11.totalCount * VersionResearchUnitExtractor.calculateDistanceToAverageUsage(average)(child1Usage)).toInt
      val modifiedInstTotalCounter21: Int = (instructionCounter22.totalCount * VersionResearchUnitExtractor.calculateDistanceToAverageUsage(average)(child2Usage)).toInt
      val modifiedInstTotalCounter1: Int = (instructionCounter1.totalCount * VersionResearchUnitExtractor.calculateDistanceToAverageUsage(average)(endpoint1Usage)).toInt
      val modifiedInstTotalCounter2: Int = (instructionCounter2.totalCount * VersionResearchUnitExtractor.calculateDistanceToAverageUsage(average)(endpoint2Usage)).toInt

      val newInstrCov: Double = (modifiedInstCoveredCounter11 + modifiedInstCoveredCounter21 + modifiedInstCoveredCounter1 + modifiedInstCoveredCounter2).toDouble /
        (modifiedInstTotalCounter11 + modifiedInstTotalCounter21 + modifiedInstTotalCounter1 + modifiedInstTotalCounter2).toDouble

      val oldInstrCov: Double = (instructionCounter11.coveredCount + instructionCounter22.coveredCount + instructionCounter2.coveredCount + instructionCounter1.coveredCount).toDouble /
        (instructionCounter11.totalCount + instructionCounter22.totalCount + instructionCounter2.totalCount + instructionCounter1.totalCount).toDouble

      val modifiedBranchCoveredCounter11: Int = (branchCounter11.coveredCount * VersionResearchUnitExtractor.calculateDistanceToAverageUsage(average)(child1Usage)).toInt
      val modifiedBranchCoveredCounter21: Int = (branchCounter22.coveredCount * VersionResearchUnitExtractor.calculateDistanceToAverageUsage(average)(child2Usage)).toInt
      val modifiedBranchCoveredCounter1: Int = (branchCounter1.coveredCount * VersionResearchUnitExtractor.calculateDistanceToAverageUsage(average)(endpoint1Usage)).toInt
      val modifiedBranchCoveredCounter2: Int = (branchCounter2.coveredCount * VersionResearchUnitExtractor.calculateDistanceToAverageUsage(average)(endpoint2Usage)).toInt

      val modifiedBranchTotalCounter11: Int = (branchCounter11.totalCount * VersionResearchUnitExtractor.calculateDistanceToAverageUsage(average)(child1Usage)).toInt
      val modifiedBranchTotalCounter21: Int = (branchCounter22.totalCount * VersionResearchUnitExtractor.calculateDistanceToAverageUsage(average)(child2Usage)).toInt
      val modifiedBranchTotalCounter1: Int = (branchCounter1.totalCount * VersionResearchUnitExtractor.calculateDistanceToAverageUsage(average)(endpoint1Usage)).toInt
      val modifiedBranchTotalCounter2: Int = (branchCounter2.totalCount * VersionResearchUnitExtractor.calculateDistanceToAverageUsage(average)(endpoint2Usage)).toInt

      val newBranchCov: Double = (modifiedBranchCoveredCounter11 + modifiedBranchCoveredCounter21 + modifiedBranchCoveredCounter1 + modifiedBranchCoveredCounter2).toDouble /
        (modifiedBranchTotalCounter11 + modifiedBranchTotalCounter21 + modifiedBranchTotalCounter1 + modifiedBranchTotalCounter2).toDouble

      val oldBranchCov: Double = (branchCounter11.coveredCount + branchCounter22.coveredCount + branchCounter2.coveredCount + branchCounter1.coveredCount).toDouble /
        (branchCounter11.totalCount + branchCounter22.totalCount + branchCounter2.totalCount + branchCounter1.totalCount).toDouble

      val expectedVRUnit = VersionResearchUnit(
        versionName = versionModel.versionName,
        bugQty = 2,
        totalUsage = 150,
        timeSpentOnProduction = 0,
        oldInstructionCoverage = oldInstrCov,
        newInstructionCoverage = newInstrCov,
        0.5714285714285714,
        oldBranchCoverage = oldBranchCov,
        newBranchCoverage = newBranchCov,
        0.5)

      researchManager.expectMsg(3.seconds, expectedVRUnit)
    }
  }
}