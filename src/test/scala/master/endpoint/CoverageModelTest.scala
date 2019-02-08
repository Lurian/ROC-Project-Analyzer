package master.endpoint

import master.mock.ModelMock
import master.mongodb.coverage._
import org.scalamock.scalatest.MockFactory
import org.scalatest.FreeSpec
import org.scalatest.Matchers._

class CoverageModelTest extends FreeSpec with MockFactory {

  trait CoverageModelFixture {
    val total_11 = 20; val covered_11 = 5; val missed_11 = 15
    val instructionCounter11 = ModelMock.coverageCounterMock(CoverageType.INSTRUCTION, total_11, covered_11, missed_11)
    val methodCoverage11 = ModelMock.methodCoverageMock(name = "method1", instructionCounter = instructionCounter11)

    val total_12 = 30; val covered_12 = 28; val missed_12 = 2
    val instructionCounter12 = ModelMock.coverageCounterMock(CoverageType.INSTRUCTION, total_12, covered_12, missed_12)
    val methodCoverage12 = ModelMock.methodCoverageMock(name = "method2", instructionCounter = instructionCounter12)

    val classCoverage1 = ModelMock.classCoverageMock("org/test/class1", methodList = List(methodCoverage11, methodCoverage12))

    val total_21 = 100; val covered_21 = 90; val missed_21 = 10
    val instructionCounter21 = ModelMock.coverageCounterMock(CoverageType.INSTRUCTION, total_21, covered_21, missed_21)
    val methodCoverage21 = ModelMock.methodCoverageMock(name = "method1", instructionCounter = instructionCounter21)

    val classCoverage2 = ModelMock.classCoverageMock("org/test/class2", methodList = List(methodCoverage21))

    val packageCoverage = ModelMock.packageCoverageMock(classList = List(classCoverage1, classCoverage2))
    val bundleCoverage = ModelMock.bundleCoverageMock(packageList = List(packageCoverage))
    val coverageModel = ModelMock.coverageModelMock(bundleCoverage = bundleCoverage)
  }

  "A CoverageModel" - {
    "when applying the weight map" - {
      "should apply each weight to the instruction counters" in {
        val instructionCounter = ModelMock.coverageCounterMock(CoverageType.INSTRUCTION, 20, 10, 10)
        val methodCoverage = ModelMock.methodCoverageMock(name = "method", instructionCounter = instructionCounter)

        val classCoverage = ModelMock.classCoverageMock(name = "org/test/class", methodList = List(methodCoverage))
        val packageCoverage = ModelMock.packageCoverageMock(classList = List(classCoverage))
        val bundleCoverage = ModelMock.bundleCoverageMock(packageList = List(packageCoverage))
        val coverageModel = ModelMock.coverageModelMock(bundleCoverage = bundleCoverage)

        val methodSignature = "org.test.class.method"
        val weightMap = Map(methodSignature -> 2.0)
        val newCoverageModel = coverageModel.applyWeightMap(weightMap)

        val oldInst =  newCoverageModel.getMethodCoverage(methodSignature).get.instructionCounter
        val returnedInstrCounter =  newCoverageModel.getMethodCoverage(methodSignature).get.instructionCounter

        returnedInstrCounter.coveredCount should equal(20)
        returnedInstrCounter.missedCount should equal(20)
        returnedInstrCounter.coveredRatio should equal(0.5)
      }

      "should apply each weight to the branch counters" in {
        val branchCounter = ModelMock.coverageCounterMock(CoverageType.BRANCH, 20, 10, 10)
        val methodCoverage = ModelMock.methodCoverageMock(name = "method", branchCounter = branchCounter)

        val classCoverage = ModelMock.classCoverageMock("org/test/class", methodList = List(methodCoverage))
        val packageCoverage = ModelMock.packageCoverageMock(classList = List(classCoverage))
        val bundleCoverage = ModelMock.bundleCoverageMock(packageList = List(packageCoverage))
        val coverageModel = ModelMock.coverageModelMock(bundleCoverage = bundleCoverage)

        val methodSignature = "org.test.class.method"
        val weightMap = Map(methodSignature -> 2.0)
        val newCoverageModel = coverageModel.applyWeightMap(weightMap)

        val returnedBranchCounter =  newCoverageModel.getMethodCoverage(methodSignature).get.branchCounter

        returnedBranchCounter.coveredCount should equal(20)
        returnedBranchCounter.missedCount should equal(20)
        returnedBranchCounter.coveredRatio should equal(0.5)
      }

      "should apply each weight to the different methods in different classes" in new CoverageModelFixture {
        val method11Signature = "org.test.class1.method1"; val weight_11 = 2.0
        val method12Signature = "org.test.class1.method2"; val weight_12 = 5.0
        val method21Signature = "org.test.class2.method1"; val weight_21 = 10.0
        val weightMap = Map(method11Signature -> weight_11, method12Signature -> weight_12, method21Signature -> weight_21)
        val newCoverageModel = coverageModel.applyWeightMap(weightMap)

        val returnedInstrCounter11 =  newCoverageModel.getMethodCoverage(method11Signature).get.instructionCounter
        returnedInstrCounter11.coveredCount should equal(covered_11 * weight_11)
        returnedInstrCounter11.missedCount should equal(missed_11 * weight_11)
        returnedInstrCounter11.coveredRatio should equal(covered_11.toDouble / total_11.toDouble)

        val returnedInstrCounter12 =  newCoverageModel.getMethodCoverage(method12Signature).get.instructionCounter
        returnedInstrCounter12.coveredCount should equal(covered_12 * weight_12)
        returnedInstrCounter12.missedCount should equal(missed_12 * weight_12)
        returnedInstrCounter12.coveredRatio should equal(covered_12.toDouble / total_12.toDouble)

        val returnedInstrCounter21 =  newCoverageModel.getMethodCoverage(method21Signature).get.instructionCounter
        returnedInstrCounter21.coveredCount should equal(covered_21 * weight_21)
        returnedInstrCounter21.missedCount should equal(missed_21 * weight_21)
        returnedInstrCounter21.coveredRatio should equal(covered_21.toDouble / total_21.toDouble)
      }

      "should return all signatures" in new CoverageModelFixture {
        val method11Signature = "org.test.class1.method1"; val weight_11 = 2.0
        val method12Signature = "org.test.class1.method2"; val weight_12 = 5.0
        val method21Signature = "org.test.class2.method1"; val weight_21 = 10.0

        val returnedInstrCounter21 = coverageModel.getSignatureToInstrCounterMap()

        println(returnedInstrCounter21)
      }
    }

    "when searching for method coverage" - {
      val methodCoverage11 = ModelMock.methodCoverageMock(name = "method1")
      val methodCoverage12 = ModelMock.methodCoverageMock(name = "method2")
      val methodCoverage13 = ModelMock.methodCoverageMock(name = "method3")
      val classCoverage1 = ModelMock.classCoverageMock("class1", methodList = List(methodCoverage11, methodCoverage12, methodCoverage13))

      val methodCoverage21 = ModelMock.methodCoverageMock(name = "method1")
      val classCoverage2 = ModelMock.classCoverageMock("org/test/class2", superName = "class3", methodList = List(methodCoverage21))

      val methodCoverage31 = ModelMock.methodCoverageMock(name = "method3")
      val classCoverage3 = ModelMock.classCoverageMock("org/test/class3", methodList = List(methodCoverage31))

      val packageCoverage = ModelMock.packageCoverageMock(classList = List(classCoverage1, classCoverage2, classCoverage3))
      val bundleCoverage = ModelMock.bundleCoverageMock(packageList = List(packageCoverage))
      val coverageModel = ModelMock.coverageModelMock(bundleCoverage = bundleCoverage)

      val method11Signature = "org.test.class1.method1"
      val method12Signature = "org.test.class1.method2"
      val method21Signature = "org.test.class2.method1"
      val method23Signature = "org.test.class2.method3"

      "should find a method with unique name" in {
        coverageModel.getMethodCoverage(method12Signature) should equal(Some(methodCoverage12))
      }

      "should find a method with not unique name by checking the class name" in {
        coverageModel.getMethodCoverage(method11Signature) should equal(Some(methodCoverage11))
        coverageModel.getMethodCoverage(method21Signature) should equal(Some(methodCoverage21))
      }

      "should find a super method" in {
        coverageModel.getMethodCoverage(method23Signature) should equal(Some(methodCoverage31))
      }
    }
  }
}