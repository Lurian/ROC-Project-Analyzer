package master.research

import akka.event.{LoggingAdapter, NoLogging}
import master.mock.ModelMock
import master.research.MethodGraph.{Edge, Node}
import master.research.method.BugExtractor.MethodToBugAnalysis
import org.scalamock.scalatest.MockFactory
import org.scalatest.FreeSpec
import org.scalatest.Matchers._


class MethodGraphTest extends FreeSpec with MockFactory {

  implicit val log: LoggingAdapter = NoLogging
  implicit val sumUpdateFunction: (Int, Int) => Int = {_+_}

  "An MethodGraph" - {
    "with a map empty" - {
      val graph = MethodGraph()

      "should return 1 when asking for nextId" in {
        graph.nextId should equal(2)
      }

      "should add a rootNode and associate a new ID(1) to it and add usage from User to the new root node" in {
        val restMethod = ModelMock.appMethodMock()
        val restUsage = 100

        val nextId = 2
        val newGraph = graph.addRootNode(restMethod, restUsage)

        newGraph.nodeMap should contain (restMethod.signature -> Node(nextId, totalUsage = restUsage))
        newGraph.edgeMap should contain (Edge(MethodGraph.userID, nextId)-> restUsage)
      }
    }

    "with map nonEmpty" - {
      val nonEmptyMap = Map.empty ++ (2 to 10).map((x) => x.toString -> Node(x))
      val graph = MethodGraph(nonEmptyMap)

      "should return the map length plus one when asking for nextId" in {
        graph.nextId should equal(nonEmptyMap.size + 2)
      }

      "should add a rootNode and associate a new ID(map.size + 1) to it" in {
        val restMethod = ModelMock.appMethodMock()
        val restUsage = 100

        val newGraph = graph.addRootNode(restMethod, restUsage)

        newGraph.nodeMap should contain (restMethod.signature -> Node(nonEmptyMap.size + 2, totalUsage = restUsage))
      }
    }

    "regardless of the edgeList size" - {
      val rootMethod = ModelMock.appMethodMock(methodDeclaration = "rootMethod()")
      val rootId = 2
      val nonEmptyMap = Map.empty + (rootMethod.signature -> Node(rootId))
      val usage = 10

      "with a childNode not present in the node map should add it to the map and add the edge in the list" in {
        val graph = MethodGraph(nonEmptyMap, Map.empty)
        val childMethod = ModelMock.appMethodMock(methodDeclaration = "childMethod()")

        val newGraph = graph.addChildNode(rootMethod, childMethod, usage)

        newGraph.nodeMap should contain (childMethod.signature -> Node(graph.nextId, totalUsage = usage))
        newGraph.edgeMap should contain (Edge(rootId, graph.nextId) -> usage)
      }

      "with a childNode present in the node map should reuse the id in the map and add the edge in the list" in {
        val childMethod = ModelMock.appMethodMock(methodDeclaration = "childMethod()")
        val childId = rootId + 1
        val mapWithChildNode = nonEmptyMap + (childMethod.signature -> Node(childId))
        val graph = MethodGraph(mapWithChildNode, Map.empty)

        val newGraph = graph.addChildNode(rootMethod, childMethod, usage)

        newGraph.nodeMap should contain (childMethod.signature -> Node(childId, totalUsage = usage))
        newGraph.edgeMap should contain (Edge(rootId, childId) -> usage)
      }
    }

    val childMethod11 = ModelMock.appMethodMock(methodDeclaration = "childNode11()")
    val childInCommon = ModelMock.appMethodMock(methodDeclaration = "childNode12()")
    val rootMethod1 = ModelMock.appMethodMock(methodDeclaration = "rootMethod1()")

    val methodTree11 = ModelMock.methodTreeMock(childMethod11, root = Some(rootMethod1))
    val methodTree12 = ModelMock.methodTreeMock(childInCommon, root = Some(rootMethod1))
    val methodTree1 = ModelMock.methodTreeMock(rootMethod1, Some(List(methodTree11,methodTree12)))

    val childMethod21 = ModelMock.appMethodMock(methodDeclaration = "childNode21()")
    val rootMethod2 = ModelMock.appMethodMock(methodDeclaration = "rootMethod2()")

    val methodTree21 = ModelMock.methodTreeMock(childMethod21, root = Some(rootMethod2))
    val methodTree22 = ModelMock.methodTreeMock(childInCommon, root = Some(rootMethod2))
    val methodTree2 = ModelMock.methodTreeMock(rootMethod2, Some(List(methodTree21, methodTree22)))

    val usageRoot1 = 100
    val usageRoot2 = 200

    "addMethodTree function" - {
      "should span the graph based on the list of trees passed" in {
        val expectedNodeMap = Map.empty +
          (MethodGraph.userSignature -> Node(1)) +
          (rootMethod1.signature -> Node(2, totalUsage = usageRoot1)) +
          (rootMethod2.signature -> Node(3, totalUsage = usageRoot2)) +
          (childMethod11.signature -> Node(4, totalUsage = usageRoot1)) +
          (childInCommon.signature -> Node(5, totalUsage = usageRoot1 + usageRoot2)) +
          (childMethod21.signature -> Node(6, totalUsage = usageRoot2))
        val expectedEdgeList = Map(Edge(MethodGraph.userID, 2) -> usageRoot1, Edge(MethodGraph.userID, 3) -> usageRoot2,
          Edge(2,4) -> usageRoot1, Edge(2,5) -> usageRoot1, Edge(3,6) -> usageRoot2, Edge(3,5) -> usageRoot2)

        val methodTreeMap = Map(methodTree1 -> usageRoot1, methodTree2 -> usageRoot2)

        val returnedMap = MethodGraph(Map.empty, Map.empty).addMethodTree(methodTreeMap, 2)

        returnedMap.nodeMap should equal(expectedNodeMap)
        returnedMap.edgeMap should equal(expectedEdgeList)
      }

      "should limit the height based on the maxHeight value" in {
        val expectedNodeMap = Map.empty + (MethodGraph.userSignature -> Node(1)) + (rootMethod1.signature -> Node(2, totalUsage = usageRoot1)) +
          (rootMethod2.signature -> Node(3, totalUsage = usageRoot2))
        val expectedEdgeList = Map.empty + (Edge(MethodGraph.userID, 2) -> usageRoot1) +
          (Edge(MethodGraph.userID, 3) -> usageRoot2)

        val methodTreeMap = Map(methodTree1 -> usageRoot1, methodTree2 -> usageRoot2)

        val returnedGraph = MethodGraph(Map.empty, Map.empty).addMethodTree(methodTreeMap, 1)

        returnedGraph.nodeMap should equal(expectedNodeMap)
        returnedGraph.edgeMap should equal(expectedEdgeList)
      }
    }

    "signatureToUsage map should return all method signatures and usages based on the edgeList" in {
      val nodeMap = Map.empty +
        (rootMethod1.signature -> Node(2, totalUsage = usageRoot1)) +
        (rootMethod2.signature -> Node(3, totalUsage = usageRoot2)) +
        (childMethod11.signature -> Node(4, totalUsage = usageRoot1)) +
        (childInCommon.signature -> Node(5, totalUsage = usageRoot1 + usageRoot2)) +
        (childMethod21.signature -> Node(6, totalUsage = usageRoot2))
      val edgeMap = Map(Edge(MethodGraph.userID, 2) -> usageRoot1, Edge(MethodGraph.userID, 3) -> usageRoot2,
        Edge(2,4) -> usageRoot1, Edge(2,5) -> usageRoot1, Edge(3,6) -> usageRoot2, Edge(3,5) -> usageRoot2)

      val graph = MethodGraph(nodeMap, edgeMap)

      val expectedSignatureToUsageMap = Map.empty + (rootMethod1.signature -> usageRoot1) + (rootMethod2.signature -> usageRoot2) +
        (childMethod11.signature -> usageRoot1) + (childInCommon.signature -> (usageRoot1 + usageRoot2)) + (childMethod21.signature -> usageRoot2)

      graph.signatureToUsageMap should equal(expectedSignatureToUsageMap)
    }

    "when working with bug analysis" - {
      val nodeMap = Map.empty +
        (rootMethod1.signature -> Node(2, totalUsage = usageRoot1)) +
        (rootMethod2.signature -> Node(3, totalUsage = usageRoot2)) +
        (childMethod11.signature -> Node(4, totalUsage = usageRoot1)) +
        (childInCommon.signature -> Node(5, totalUsage = usageRoot1 + usageRoot2)) +
        (childMethod21.signature -> Node(6, totalUsage = usageRoot2))
      val edgeMap = Map(Edge(MethodGraph.userID, 2) -> usageRoot1, Edge(MethodGraph.userID, 3) -> usageRoot2,
        Edge(2,4) -> usageRoot1, Edge(2,5) -> usageRoot1, Edge(3,6) -> usageRoot2, Edge(3,5) -> usageRoot2)

      val bugList1 = List("bug1", "bug2")
      val bugList2 = List("bug3")
      val bugList11 = List("bug1")
      val bugListInCommon = List("bug1", "bug2", "bug3")

      val signatureToBugMap: Map[String, List[String]] = Map.empty +
        (rootMethod1.signature ->bugList1) +
        (rootMethod2.signature -> bugList2) +
        (childMethod11.signature -> bugList11) +
        (childInCommon.signature -> bugListInCommon)
      val methodToBugAnalysis = MethodToBugAnalysis(signatureToBugMap)

      val graph = MethodGraph(nodeMap, edgeMap)
      val newGraph = graph.addMethodToBugAnalysis(methodToBugAnalysis)

      "addMethodToBugAnalysis should correctly add bugQty for each method signature compatible" in {
        val expectedNodeMap = Map.empty +
          (MethodGraph.userSignature -> Node(1)) +
          (rootMethod1.signature -> Node(2, bugList1.toSet, usageRoot1)) +
          (rootMethod2.signature -> Node(3, bugList2.toSet, usageRoot2)) +
          (childMethod11.signature -> Node(4, bugList11.toSet, usageRoot1)) +
          (childInCommon.signature -> Node(5, bugListInCommon.toSet, usageRoot1 + usageRoot2)) +
          (childMethod21.signature -> Node(6, Set.empty, usageRoot2))

        newGraph.nodeMap should equal(expectedNodeMap)
      }

      "spreadBugImpact should correctly calculate all reachable bugs and update the nodes" in {
        val bugSpreadMap = newGraph.spreadBugImpact

        val expectedNodeMap = Map.empty +
          (MethodGraph.userSignature -> Node(1, bugListInCommon.toSet)) +
          (rootMethod1.signature -> Node(2, bugListInCommon.toSet, usageRoot1)) +
          (rootMethod2.signature -> Node(3, bugListInCommon.toSet, usageRoot2)) +
          (childMethod11.signature -> Node(4, bugList11.toSet, usageRoot1)) +
          (childInCommon.signature -> Node(5, bugListInCommon.toSet, usageRoot1 + usageRoot2)) +
          (childMethod21.signature -> Node(6, Set.empty, usageRoot2))

        bugSpreadMap.nodeMap should equal(expectedNodeMap)
      }
    }
  }
}