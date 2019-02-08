package master.research

import java.io.{BufferedWriter, File, FileWriter}

import master.endpoint.{Endpoint, ImpactedMethodsCalculator, MethodTree}
import master.mongodb.elastic.ElasticVersionAnalysis

/**
  * This companion object creates an instance of a TGFGenerator
  */
object TGFGenerator {

  /**
    * [[TGFGenerator]] constructor.
    * @param elasticAnalysisList [[ElasticVersionAnalysis]] containing the endpoints which are the root for the the graph.
    * @param impactedMethodsCalculator [[ImpactedMethodsCalculator]] auxliary object which calculates the CallTree for each endpoint.
    * @return A new instance of a [[TGFGenerator]]
    */
  def apply(elasticAnalysisList: ElasticVersionAnalysis, impactedMethodsCalculator: ImpactedMethodsCalculator): TGFGenerator = {
    val endpointList = elasticAnalysisList.endpointUsageList
    val sortedEndpoint = endpointList sortWith {_.usage.getOrElse(0) > _.usage.getOrElse(0)}

    val endpointMethodTreeStream: Stream[(Endpoint, MethodTree)] = sortedEndpoint.toStream.map((endpoint) => {
      val methodTree = impactedMethodsCalculator.calculateImpactedMethods(endpoint.restMethod)
      (endpoint, methodTree)
    })
    new TGFGenerator(endpointMethodTreeStream)
  }
}

/**
  * A factory class capable of generating Directed Graphs representing the possible method calls that origin of an
  * endpoint.
  * @param endpointStream [[Stream[(Endpoint, MethodTree)]]] Lazy evaluated, the MethodTree is only calculated after when necessary.
  */
class TGFGenerator(endpointStream: Stream[(Endpoint, MethodTree)]) {

  /**
    * Span a [[MethodGraph]] using the generator inner data and params
    * @param maxRootMethods [[Int]] Limiter for endpoints used.
    * @param maxHeight [[Int]] Limiter for graph height.
    * @return
    */
  def spanGraph(maxRootMethods: Int = Int.MaxValue, maxHeight: Int = Int.MaxValue): MethodGraph = {
    val prunedRootMethodsList = endpointStream.take(maxRootMethods)
    val methodTreeMap = prunedRootMethodsList.map((kv) => kv._2 -> kv._1.usage.getOrElse(0)).toMap
    MethodGraph(methodTreeMap, maxHeight, (x,y) => x+y)
  }

  /**
    * Receive a [[MethodGraph]] and export it to TGF Format file.
    * @param graph [[MethodGraph]] Limiter for endpoints used.
    * @param outputPath [[String]] Path for the TGF file.
    * @param obfuscate [[Boolean]] representing if the method names should be obfuscated.
    */
  def createTFG(graph: MethodGraph, outputPath: String, obfuscate: Boolean = false): Unit = {
    val file = new File(outputPath)
    val bw = new BufferedWriter(new FileWriter(file))

    graph.nodeMap.toList.sortBy(_._2.id) foreach { (kv) =>
      val headerString = s"${kv._2.id} ${if(obfuscate) s"M.${kv._2.id}" else kv._1}\n"
      bw.write(headerString)
    }
    bw.write("#\n")

    graph.edgeMap foreach {(kv) =>
      val edgeString = s"${kv._1.sourceId} ${kv._1.targetId} ${kv._2}\n"
      bw.write(edgeString)
    }

    bw.close()
  }

  def createGML(graph: MethodGraph, outputPath: String, obfuscate: Boolean = false): Unit = {
    val file = new File(outputPath)
    val bw = new BufferedWriter(new FileWriter(file))
    val paragraph = "  "

    bw.write(s"""<?xml version="1.0" encoding="UTF-8"?>\n""")
    bw.write(s"""<graphml xmlns="http://graphml.graphdrawing.org/xmlns"\n""")
    bw.write(s"""$paragraph xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\n""")
    bw.write(s"""$paragraph xsi:schemaLocation="http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd">"\n""")
    bw.write(s"""$paragraph <key id="bugQty" for="node" attr.name="bugQty" attr.type="int"/>""")
    bw.write(s"""$paragraph <key id="label" for="node" attr.name="label" attr.type="string"/>""")
    bw.write(s"""$paragraph <key id="totalUsage" for="node" attr.name="totalUsage" attr.type="int"/>""")
    bw.write(s"""$paragraph <key id="bugSet" for="node" attr.name="bugSet" attr.type="string"/>""")
    bw.write(s"""$paragraph <key id="usage" for="edge" attr.name="usage" attr.type="int"/>""")
    bw.write(s"""$paragraph <graph id="ProjectAnalyzer" edgedefault="directed">\n""")
    graph.nodeMap.toList.sortBy(_._2.id) foreach { (kv) =>
      val node = kv._2
      val signature = if(obfuscate) s"M.${node.id}" else kv._1
      val cleanSignature = signature.replace('>', ' ').replace('<', ' ')
      bw.write(s"""$paragraph$paragraph <node id="${node.id}">\n""")
      bw.write(s"""$paragraph$paragraph$paragraph<data key="bugQty">${node.bugSet.size}</data>\n""")
      bw.write(s"""$paragraph$paragraph$paragraph<data key="bugSet">${node.bugSet.map(_+"|").fold("")(_+_)}</data>\n""")
      bw.write(s"""$paragraph$paragraph$paragraph<data key="label">"$cleanSignature - ${node.totalUsage}"</data>\n""")
      bw.write(s"""$paragraph$paragraph$paragraph<data key="totalUsage">${node.totalUsage}</data>\n""")
      bw.write(s"""$paragraph$paragraph </node>\n""")
    }
    var edgeCount = 0
    graph.edgeMap foreach {(kv) =>
      val edge = kv._1
      val usage = kv._2
      edgeCount = edgeCount + 1
      bw.write(s"""$paragraph$paragraph <edge id="e$edgeCount" source="${edge.sourceId}" target="${edge.targetId}" >\n""")
      bw.write(s"""$paragraph$paragraph$paragraph<data key="usage">$usage</data>\n""")
      bw.write(s"""$paragraph$paragraph </edge>\n""")
    }
    bw.write(s"""$paragraph </graph>\n""")
    bw.write("</graphml>\n")
    bw.close()
  }
}