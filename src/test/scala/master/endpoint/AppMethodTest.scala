package master.endpoint

import org.scalatest.FreeSpec
import org.scalatest.Matchers._

class AppMethodTest extends FreeSpec {

  "An AppMethod" - {
    val mockClassDeclaration = "org.test.MockClass"
    val mockMethodDeclaration = "mockMethod( org.test.Parameter )"
    "when being constructed with the apply method" - {
      "should correctly extracted parameters from methodDeclaration" in {
        val appMethod = AppMethod(mockClassDeclaration, mockMethodDeclaration)
        val expectedParameters = List("org.test.Parameter")
        appMethod.parameters should equal (expectedParameters)
      }

      "should correctly extracted the methodName from methodDeclaration" in {
        val appMethod = AppMethod(mockClassDeclaration, mockMethodDeclaration)
        val expectedMethodName = "mockMethod"
        appMethod.methodName should equal (expectedMethodName)
      }

      "should correctly pass the classDeclaration to the new instance" in {
        val appMethod = AppMethod(mockClassDeclaration, mockMethodDeclaration)
        appMethod.classDeclaration should equal (mockClassDeclaration)
      }

      "should replace 'Service' with 'Bean' in the classDeclaration" in {
        val serviceClassDeclaration = "org.test.MockService"
        val appMethod = AppMethod(serviceClassDeclaration, mockMethodDeclaration)

        val expectedClassDeclaration = "org.test.MockBean"
        appMethod.classDeclaration should equal (expectedClassDeclaration)
      }
    }
    "should derive methods from its initial data such as" - {
      val appMethod = AppMethod(mockClassDeclaration, mockMethodDeclaration)
      "className" in { appMethod.className should equal ("MockClass") }
      "classPackage" in { appMethod.classPackage should equal ("org.test") }
      "signature" in { appMethod.signature should equal ("org.test.MockClass.mockMethod") }
    }
    "when being compared" - {
      val appMethod = AppMethod(mockClassDeclaration, mockMethodDeclaration)

      val otherMockClassDeclaration = "org.test.MockClass"
      val otherMockMethodDeclaration = "otherMockMethod( org.test.Parameter )"
      val otherMethod = AppMethod(otherMockClassDeclaration, otherMockMethodDeclaration)
      "should return false if the signatures are different" in {
        appMethod.signature should not equal otherMethod.signature
        appMethod should not equal otherMethod
      }

      "should return true if the signatures are equal" in {
        appMethod should equal (appMethod)

        val similarAppMethod = AppMethod(mockClassDeclaration, mockMethodDeclaration)
        appMethod.signature should equal (similarAppMethod.signature)
        appMethod should equal (similarAppMethod)
      }
    }
    "toString should should be its signature" in {
      val appMethod = AppMethod(mockClassDeclaration, mockMethodDeclaration)
      appMethod.toString should equal (appMethod.signature)
    }
  }
}