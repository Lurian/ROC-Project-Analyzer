package master.research

import master.endpoint.{AppMethod, MethodTree}
import master.research.MethodGraph.{Edge, Node}
import master.research.method.BugExtractor.MethodToBugAnalysis


object MethodGraph{
  def userID: Int = 1
  def userSignature: String = "USER"
  case class Edge(sourceId: Int, targetId: Int)
  case class Node(id: Int, bugSet: Set[String] = Set.empty, totalUsage: Int = 0)

  /**
    * Simple constructor
    * @param nodeMap Optional [[Map]] containing node mappings. The userID (1) should not be contained in this map, as it
    *                is reserved for user to endpoint usage arcs. Throws exception.
    * @param edgeMap Optional [[Map]] containing arc mappings.
    * @param usageUpdateFunction Function used to update an arc usages when it already exists.
    * @return A new empty instance of [[MethodGraph]].
    */
  def apply(nodeMap: Map[String, Node] = Map.empty, edgeMap: Map[Edge, Int] = Map.empty)(implicit usageUpdateFunction: (Int, Int) => Int): MethodGraph = {
    if(nodeMap.values.map(_.id).exists(_.equals(userID)))
      throw new RuntimeException(s"The userID:$userID is reserved for the representation of the user to endpoint usage")

    val initialNodeMap = nodeMap + (userSignature -> Node(userID))
    new MethodGraph(initialNodeMap, edgeMap)(usageUpdateFunction)
  }

  /**
    * Constructor that receives an method tree for graph spanning.
    * @param methodTreeMap [[Map]] MethodTreeMap used as source for spanning the graph.
    * @param maxHeight [[Int]] Max height used of the tree when spanning the graph.
    * @param usageUpdateFunction [[(Int, Int) => Int]] Function used to update an arc usages when it already exists.
    * @return A new instance of [[MethodGraph]] spanned from the [[MethodTree]].
    */
  def apply(methodTreeMap: Map[MethodTree, Int], maxHeight: Int, usageUpdateFunction: (Int, Int) => Int): MethodGraph = {
    val initialNodeMap = Map.empty + (userSignature -> Node(userID))
    new MethodGraph(initialNodeMap, Map.empty)(usageUpdateFunction).addMethodTree(methodTreeMap, maxHeight)
  }
}

protected class MethodGraph(val nodeMap: Map[String, Node], val edgeMap: Map[Edge, Int])(implicit val usageUpdateFunction: (Int, Int) => Int) {

  def nextId: Int = nodeMap.values.map(_.id).toList.sortWith(_>_) match {
    case Nil => 1
    case nonEmptyList => nonEmptyList.head + 1
  }

  def addRootNode(restMethod: AppMethod, usageValue: Int): MethodGraph = {
    val methodSignature = restMethod.signature
    nodeMap.get(methodSignature) match {
      case Some(Node(rootId, _, _)) =>
        // New Edge Map
        val newEdgeMap = calculateNewEdgeMap(MethodGraph.userID, rootId, usageValue)
        // New Node Map
        val newUsageValue = usageUpdateFunction(getUsageInArcs(rootId),usageValue)
        val newNodeMap = nodeMap + (methodSignature -> Node(rootId, totalUsage = newUsageValue))
        // New MethodGraph
        new MethodGraph(newNodeMap, newEdgeMap)
      case None =>
        // New Edge Map
        val newEdgeMap = calculateNewEdgeMap(MethodGraph.userID, nextId, usageValue)
        // New Node Map
        val newNodeMap = nodeMap + (methodSignature -> Node(nextId, totalUsage = usageValue))
        // New MethodGraph
        new MethodGraph(newNodeMap, newEdgeMap)
    }
  }

  def addChildNode(parentMethod: AppMethod, childMethod: AppMethod, usageValue: Int): MethodGraph = {
    val childMethodSignature = childMethod.signature
    val parentId = nodeMap(parentMethod.signature).id

    nodeMap.get(childMethodSignature) match {
      case Some(Node(childId, _, _)) =>
        // New Edge Map
        val newEdgeMap = calculateNewEdgeMap(parentId, childId, usageValue)
        // New Node Map
        val newUsageValue = usageUpdateFunction(getUsageInArcs(childId),usageValue)
        val newNodeMap = nodeMap + (childMethodSignature -> Node(childId, totalUsage = newUsageValue))
        // New MethodGraph
        new MethodGraph(newNodeMap, newEdgeMap)
      case None =>
        // New Edge Map
        val newEdgeMap = calculateNewEdgeMap(parentId, nextId, usageValue)
        // New Node Map
        val newNodeMap = nodeMap + (childMethodSignature -> Node(nextId, totalUsage = usageValue))
        // New MethodGraph
        new MethodGraph(newNodeMap, newEdgeMap)
    }
  }

  def calculateNewEdgeMap(parentId: Int, childId: Int, usageValue: Int): Map[Edge, Int] = {
    edgeMap.get(Edge(parentId, childId)) match {
      case Some(oldUsageValue) => edgeMap + (Edge(parentId, childId) -> usageUpdateFunction(oldUsageValue, usageValue) )
      case None => edgeMap + (Edge(parentId, childId) -> usageValue)
    }
  }

  def addMethodTree(methodTreeMap: Map[MethodTree, Int], maxHeight: Int): MethodGraph = {
    if(maxHeight != 0 && methodTreeMap.nonEmpty) {
      val rootMethodTreeList = methodTreeMap.keys
      val rootMethodTree = rootMethodTreeList.foldLeft(this) { (graph, methodTree) =>
        methodTree.root match {
          case Some(rootMethod) => graph.addChildNode(rootMethod, methodTree.method, methodTreeMap(methodTree))
          case None => graph.addRootNode(methodTree.method, methodTreeMap(methodTree))
        }
      }

      val childMethodTreeMap: Map[MethodTree, Int] = methodTreeMap.flatMap((kv) => {
        val childMethods = kv._1.leafs.getOrElse(List.empty)
        childMethods.map(_ -> kv._2).toMap
      })
      rootMethodTree.addMethodTree(childMethodTreeMap, maxHeight - 1)
    } else {
      this
    }
  }

  def addMethodToBugAnalysis(methodToBugAnalysis: MethodToBugAnalysis): MethodGraph = {
    val newNodeMap = (nodeMap - MethodGraph.userSignature) map { (kv) => {
      val Node(id, _, totalUsage) = kv._2
      val signature = kv._1
      val bugSet = methodToBugAnalysis.signatureToBugMap.getOrElse(signature, List.empty).toSet
      kv._1 -> Node(id, bugSet, totalUsage)
    }}
    MethodGraph(newNodeMap, edgeMap)
  }

  def spreadBugImpact: MethodGraph = calculateReachableBugs(MethodGraph.userSignature)

  def onlyRootNodes: MethodGraph = {
    val rootSignatures = getAllImpactedSignatures(MethodGraph.userID)
    val newNodeMap = nodeMap.filter((kv) => rootSignatures.contains(kv._1)) +
      (MethodGraph.userSignature -> nodeMap(MethodGraph.userSignature))
    val newEdgeMap = edgeMap.filter((kv) => kv._1.sourceId==MethodGraph.userID)
    new MethodGraph(newNodeMap, newEdgeMap)
  }

  def calculateReachableBugs(signature: String): MethodGraph = {
    val currentNode: Node = nodeMap(signature)
    val impactedSignatures = getAllImpactedSignatures(currentNode.id)

    val graphBelow = impactedSignatures.foldLeft(this) { (graph, signature) =>
      graph.calculateReachableBugs(signature)
    }

    val nodesUpdated = graphBelow.nodeMap.filter((kv) => impactedSignatures.contains(kv._1))
    val reachableBugs = nodesUpdated.values.flatMap(_.bugSet).toSet
    val newBugSet = reachableBugs ++ currentNode.bugSet
    val updatedNode = Node(currentNode.id, newBugSet, currentNode.totalUsage)

    val newNodeMap = graphBelow.nodeMap + (signature -> updatedNode)
    new MethodGraph(newNodeMap, graphBelow.edgeMap)
  }

  def getAllImpactedSignatures(id: Int): List[String] = {
    val impactedNodeIds = edgeMap.keys.filter(_.sourceId==id).map(_.targetId).toList
    nodeMap.filter((kv) => impactedNodeIds.contains(kv._2.id)).keys.toList
  }

  def getUsageInArcs(id: Int): Int = edgeMap.filter(_._1.targetId==id).values.sum

  /**
    * Creates a map containing the signature of a method and the total usage based on endpoint usage.
    * @return Map [[String]] -> [[Int]]
    */
  def signatureToUsageMap: Map[String, Int] = {
    (nodeMap - MethodGraph.userSignature) map { (kv) =>
      val signature = kv._1
      val usage = kv._2.totalUsage
      signature -> usage
    }
  }
}




