package master.endpoint

import java.util
import java.util.Collections

import akka.event.{LoggingAdapter, NoLogging}
import master.mock.ModelMock
import master.project.ExecApi
import master.util.{FileSource, SourceApi}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FreeSpec, Matchers}

import scala.xml.XML

class EndpointAnalyzerTest extends FreeSpec with MockFactory with Matchers {

  trait EndpointAnalyzerFixture {
    val sourceApi: SourceApi = mock[SourceApi]
    val execApi: ExecApi = mock[ExecApi]

    trait RestCheckerMock extends RestCheckerApi {
      override def getEndpointsFromDir: util.List[JavaEndpoint]
    }

    val restChecker: RestCheckerMock = mock[RestCheckerMock]
    val restCheckerFactory = mockFunction[String, RestCheckerMock]
    val endpointAnalyzer: EndpointAnalyzer = EndpointAnalyzer(sourceApi, execApi, restCheckerFactory)
  }

  "An EndpointAnalyzer" - {
    "epolPathToStaticAnalysis method" - {
      "should concat the pathToProject with the location of the static analysis within the project" in new EndpointAnalyzerFixture {
        val pathToProject = "path/to/project"
        val expectedPathToStaticAnalysis: String = s"$pathToProject/target/staticAnalysis.txt"
        (execApi.changeSlashesToFileSeparator _).expects(expectedPathToStaticAnalysis).onCall { arg: String => arg }

        // Execution
        endpointAnalyzer.pathToStaticAnalysisBuilder(pathToProject) should equal(expectedPathToStaticAnalysis)
      }

      "should call changeSlashesToFileSeparator after calculating the path to the static analysis" in new EndpointAnalyzerFixture {
        val pathToProject = "path/to/project"
        val changedPathToProject = "path+to+project"
        val expectedPathToStaticAnalysis: String = s"$changedPathToProject+target+staticAnalysis.txt"
        (execApi.changeSlashesToFileSeparator _).expects(s"$pathToProject/target/staticAnalysis.txt").onCall {
          arg: String => arg.replace("/", "+")
        }

        // Execution
        endpointAnalyzer.pathToStaticAnalysisBuilder(pathToProject) should equal(expectedPathToStaticAnalysis)
      }
    }

    "modifyPomPackagingToJar method" - {
      "should change the packaging element to 'jar'" in new EndpointAnalyzerFixture {
        val pathToPom = "path/to/pom.xml"
        val examplePom: String = "<project>\n  <modelVersion>4.0.0</modelVersion>\n  <groupId>com.mycompany.app</groupId>\n " +
          " <artifactId>my-app</artifactId>\n  <packaging>war</packaging>\n  <version>1</version>\n</project>"
        (sourceApi.loadXML _).expects(pathToPom).returns(XML.loadString(examplePom))

        val expectedPom: String = "<project>\n  <modelVersion>4.0.0</modelVersion>\n  <groupId>com.mycompany.app</groupId>\n " +
          " <artifactId>my-app</artifactId>\n  <packaging>jar</packaging>\n  <version>1</version>\n</project>"
        (sourceApi.saveXML _).expects(pathToPom, XML.loadString(expectedPom))

        // Execution
        endpointAnalyzer.modifyPomPackagingToJar(pathToPom)
      }
    }

    "doJarPackaging method" - {
      "should modify the pom.xml packaging to jar the packaging element to 'jar' and execute the packaging" in new EndpointAnalyzerFixture {
        // modifying pom.xml part
        val pathToProject = "path/to/project"
        val pathToPom = s"$pathToProject/pom.xml"
        (execApi.changeSlashesToFileSeparator _).expects(pathToPom).onCall { arg: String => arg }

        val examplePom: String = "<project>\n  <modelVersion>4.0.0</modelVersion>\n  <groupId>com.mycompany.app</groupId>\n " +
          " <artifactId>my-app</artifactId>\n  <packaging>war</packaging>\n  <version>1</version>\n</project>"
        (sourceApi.loadXML _).expects(pathToPom).returns(XML.loadString(examplePom))

        val expectedPom: String = "<project>\n  <modelVersion>4.0.0</modelVersion>\n  <groupId>com.mycompany.app</groupId>\n " +
          " <artifactId>my-app</artifactId>\n  <packaging>jar</packaging>\n  <version>1</version>\n</project>"
        (sourceApi.saveXML _).expects(pathToPom, XML.loadString(expectedPom))

        // packaging part
        val expectedCommand =  s"mvn -f $pathToPom package -DskipTests"
        (execApi.exec _).expects(expectedCommand)

        // Execution
        endpointAnalyzer.doJarPackaging(pathToProject)
      }
    }

    "doStaticAnalysis method" - {
      "should do the Static Analysis" in new EndpointAnalyzerFixture {
        // modifying pom.xml part
        val pathToProject = "path/to/project"
        val pathToJar = s"$pathToProject/target/epol-server.jar"
        // Checking if jar file exists
        (execApi.changeSlashesToFileSeparator _).expects(pathToJar).onCall { arg: String => arg }
        (sourceApi.fileExists(_:String)).expects(pathToJar).returns(true)

        // Static analysis
        val pathToJavaCallgraphJar = "./src/main/resources/javacg-0.1-SNAPSHOT-static.jar"
        (execApi.changeSlashesToFileSeparator _).expects(pathToJavaCallgraphJar).onCall { arg: String => arg }
        val analysisCommand = s"java -jar $pathToJavaCallgraphJar $pathToJar"

        val staticAnalysisText = "staticAnalysis"
        (execApi.execReturn _).expects(analysisCommand).returns(staticAnalysisText)

        val expectedPathToStaticAnalysis: String = s"$pathToProject/target/staticAnalysis.txt"
        (execApi.changeSlashesToFileSeparator _).expects(expectedPathToStaticAnalysis).onCall { arg: String => arg }
        (sourceApi.writeToFile _).expects(staticAnalysisText, expectedPathToStaticAnalysis)

        // Execution
        endpointAnalyzer.doStaticAnalysis(pathToProject)
      }
    }

    "getAllEndpoints method" - {
      "should get all the endpoints through the rest checker" in new EndpointAnalyzerFixture {
        val pathToProject = "path/to/project"
        restCheckerFactory.expects(pathToProject).returns(restChecker)

        val javaEndpoint: JavaEndpoint = ModelMock.javaEndpointMock()
        val arrayListJavaEndpoint: util.List[JavaEndpoint] = Collections.singletonList(javaEndpoint)
        (restChecker.getEndpointsFromDir _).expects().returns(arrayListJavaEndpoint)

        // Execution
        endpointAnalyzer.getAllEndpoints(pathToProject) should equal(List(Endpoint(javaEndpoint)))
      }
    }

    "analyzeEndpoint method" - {

      implicit val log: LoggingAdapter = NoLogging

      def lines() = List(
        "M:br.gov.dpf.epol.restClass1:method1() (M)br.gov.dpf.epol.class1:method1()",
        "M:br.gov.dpf.epol.restClass1:method1() (M)br.gov.dpf.epol.class2:method2()",
        "M:br.gov.dpf.epol.restClass2:method1() (M)br.gov.dpf.epol.class1:method1()").toIterator

      "should analyze all methods impacted by the endpoints of a version" in new EndpointAnalyzerFixture {
        // Check if static analysis file exists
        val pathToStaticAnalysis = "path/to/static/analysis"
        (sourceApi.fileExists(_: String)).expects(pathToStaticAnalysis).returns(true)

        // Get All Endpoints
        val pathToProject = "path/to/project"
        restCheckerFactory.expects(pathToProject).returns(restChecker)

        val javaEndpoint1 = ModelMock.javaEndpointMock("br.gov.dpf.epol.restClass1", "method1()")
        val javaEndpoint2 = ModelMock.javaEndpointMock("br.gov.dpf.epol.restClass2", "method1()")
        val arrayListJavaEndpoint: util.List[JavaEndpoint] = util.Arrays.asList(javaEndpoint1, javaEndpoint2)
        (restChecker.getEndpointsFromDir _).expects().returns(arrayListJavaEndpoint)

        // Create new instance of ImpactedMethodsCalculator
        val fileStub: FileSource = stub[FileSource]
        (fileStub.getLines _).when().returns(lines())
        (sourceApi.getSource(_: String)).expects(pathToStaticAnalysis).returns(Some(fileStub))

        val restMethod1 = ModelMock.appMethodMock("br.gov.dpf.epol.restClass1", "method1()")
        val childMethod11 = ModelMock.appMethodMock("br.gov.dpf.epol.class1", "method1()")
        val childMethod22 = ModelMock.appMethodMock("br.gov.dpf.epol.class2", "method2()")
        val endpoint1 = ModelMock.endpointMock(restMethod1)

        val restMethod2 = ModelMock.appMethodMock("br.gov.dpf.epol.restClass2", "method1()")
        val endpoint2 = ModelMock.endpointMock(restMethod2)

        val expectedResult: Map[Endpoint, List[AppMethod]] = Map(
          endpoint1 -> List(restMethod1, childMethod11, childMethod22),
          endpoint2 -> List(restMethod2, childMethod11)
        )

        // Execution
        endpointAnalyzer.analyzeEndpoint(pathToProject, Some(pathToStaticAnalysis)) should equal(expectedResult)
      }
    }
  }
}
