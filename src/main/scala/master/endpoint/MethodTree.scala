package master.endpoint

import master.endpoint.usage.MethodRelationship


/**
  * Companion Object for [[MethodTree]]
  */
object MethodTree {
  /**
    * [[MethodTree]] constructor.
    *
    * Creates the tree based on the Map of [[AppMethod]] -> [[MethodRelationship]].
    * Already visited methods will not be included again the tree even if they are called by different methods.
    * @param method [[AppMethod]] Current method having its tree spanned.
    * @param methodMap [[Map[AppMethod, List[MethodRelationship]] Map containing the relationship, call relationship, between methods.
    * @param visitedMethods  Set[AppMethod] Auxiliary structure to help keep track of already visited methods while recursive spanning the tree.
    * @param rootMethod  Option[AppMethod] Optional root method
    * @return An instance of [[MethodTree]]
    */
  def apply(method: AppMethod, methodMap: Map[AppMethod, List[MethodRelationship]], visitedMethods: Set[AppMethod], rootMethod: Option[AppMethod] = None): MethodTree = {
    val listOfCalledMethodsOption = methodMap.find(_._1.equals(method))

    if (listOfCalledMethodsOption.isDefined) {
      val listOfCalledMethods: List[AppMethod] = listOfCalledMethodsOption.get._2 map {_.targetMethod}

      // Filtering methods which were already visited
      def checkIfAlreadyVisited(appMethod: AppMethod) = visitedMethods.exists {_.equals(appMethod)}
      val filteredListOfCalledMethods = listOfCalledMethods filter { (calledMethod) => !checkIfAlreadyVisited(calledMethod)}

      // Updating visited methods list
      val newVisitedMethods = visitedMethods ++ filteredListOfCalledMethods

      // Calculating leafs trees

      val leafs = filteredListOfCalledMethods map { (calledMethod) => apply(calledMethod, methodMap, newVisitedMethods, Some(method))}

      new MethodTree(method, Option(leafs), rootMethod)
    } else {
      new MethodTree(method, None, rootMethod)
    }
  }
}

/**
  * A Recursive Object representing a Call Method Tree.
  * The child nodes are all the methods reachable within the all possible executions of the root method.
  *
  * The purpose of this tree is to represent all the methods reached by a method and some information about its structure.
  * @param method [[AppMethod]] the method contained in the node.
  * @param leafs [[Option[List[MethodTree]] Possible list of child node.
  * @param root Possible root [[AppMethod]]
  */
case class MethodTree(method: AppMethod, leafs: Option[List[MethodTree]], root: Option[AppMethod]) {

  /**
    * Prints to STDOUT the Method Tree using indentation to express hierarchy.
    * @param height [[Int]] auxiliary parameter to store the current height being printed.
    */
  def prettyPrint(height: Int = 0): Unit = {
    val prefix: String = "# " * height
    println(s"$prefix$method")
    if (leafs.isDefined) leafs.get foreach {
      _.prettyPrint(height + 1)
    }
  }

  /**
    * Flatten the tree retuning all the AppMethods contained.
    * @return [[Set[AppMethod]]] All AppMethods in the tree.
    */
  def getAllMethodsReached: Set[AppMethod] = {
    if (leafs.isDefined) Set(method) ++ (leafs.get flatMap {
      _.getAllMethodsReached
    })
    else Set(method)
  }
}