package master.research

import akka.event.{LoggingAdapter, NoLogging}
import master.endpoint.{Endpoint, MethodTree}
import master.mock.ModelMock
import master.research.MethodGraph.{Edge, Node}
import org.scalamock.scalatest.MockFactory
import org.scalatest.FreeSpec
import org.scalatest.Matchers._


class TGFGeneratorTest extends FreeSpec with MockFactory {

  implicit val log: LoggingAdapter = NoLogging

  "An TFGGenerator" - {
    "spanGraph function" - {
      "should add all root methods to the graph and method in the usage list as child nodes" in {
        val usageRoot1 = 100
        val usageRoot2 = 200

        val childMethod11 = ModelMock.appMethodMock(methodDeclaration = "childNode11()")
        val childMethod12 = ModelMock.appMethodMock(methodDeclaration = "childNode12()")
        val childMethods1 = List(childMethod11, childMethod12)
        val rootMethod1 = ModelMock.appMethodMock(methodDeclaration = "rootMethod1()")
        val rootEndpoint1 = ModelMock.endpointMock(restMethod = rootMethod1, usageOption = Some(usageRoot1)).addImpactedMethodsList(childMethods1)

        val childMethod21 = ModelMock.appMethodMock(methodDeclaration = "childNode21()")
        val rootMethod2 = ModelMock.appMethodMock(methodDeclaration = "rootMethod2()")
        val rootEndpoint2 = ModelMock.endpointMock(restMethod = rootMethod2, usageOption = Some(usageRoot2)).addImpactedMethodsList(List(childMethod21))

        val expectedMap = Map.empty +
          (MethodGraph.userSignature -> Node(MethodGraph.userID, Set(), 0)) +
          (rootMethod1.signature -> Node(2, Set(), usageRoot1)) +
          (rootMethod2.signature -> Node(3, Set(), usageRoot2)) +
          (childMethod11.signature -> Node(4, Set(), usageRoot1)) +
          (childMethod12.signature -> Node(5, Set(), usageRoot1)) +
          (childMethod21.signature -> Node(6, Set(), usageRoot2))
        val expectedEdgeMap = Map(Edge(1,2) -> usageRoot1, Edge(1,3) -> usageRoot2, Edge(2,4) -> usageRoot1,
          Edge(2,5) -> usageRoot1, Edge(3,6) -> usageRoot2)

        val methodTree11 = ModelMock.methodTreeMock(childMethod11, root = Some(rootMethod1))
        val methodTree12 = ModelMock.methodTreeMock(childMethod12, root = Some(rootMethod1))
        val methodTree1 = ModelMock.methodTreeMock(rootMethod1, Some(List(methodTree11,methodTree12)))
        val methodTree21 = ModelMock.methodTreeMock(childMethod21, root = Some(rootMethod2))
        val methodTree2 = ModelMock.methodTreeMock(rootMethod2, Some(List(methodTree21)))

        val endpointMethodTreeStream: Stream[(Endpoint, MethodTree)] = Stream((rootEndpoint1, methodTree1),(rootEndpoint2, methodTree2))
        val generator: TGFGenerator = new TGFGenerator(endpointMethodTreeStream)

        val returnedGraph = generator.spanGraph(2, 2)

        returnedGraph.nodeMap should equal(expectedMap)
        returnedGraph.edgeMap should equal(expectedEdgeMap)
      }
    }
  }
}