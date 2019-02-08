package master.javaParser.model

import master.javaParser.JavaImpact

import scala.collection.JavaConverters._


object Impact {
  def apply(methods: collection.mutable.Set[String], fileName: String): Impact = new Impact(methods.toList, fileName)

  def apply(javaImpact: JavaImpact): Impact = {
    val methods: collection.mutable.Set[String] = javaImpact.getMethods.asScala
    new Impact(methods.toList, javaImpact.getFileName)
  }
}

case class Impact(methods: List[String], fileName: String)