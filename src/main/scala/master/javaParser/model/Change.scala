package master.javaParser.model

import java.util
import master.javaParser.{JavaChange, JavaLine}

import scala.collection.JavaConverters._

object Change{
  def apply(diff: String, fileName: String): Change = new Change(getLinesFromDiff(diff), fileName)

  def getLinesFromDiff(diff: String): List[Lines] = {
    val myRe = """@@\s\-(\d+),(\d+)\s\+(\d+),(\d+)\s@@""".r
    val lines = for (m <- myRe.findAllMatchIn(diff))
      yield Lines(m.group(3).toInt, m.group(3).toInt + m.group(4).toInt)
    lines.toList
  }
}

case class Change(lines: List[Lines], fileName: String) {

  def getImpact(methods : List[Method] ) : List[Method] = {
      methods filter {
        (method) => lines exists {
          (line) => method.isImpacted(line)
        }
      }
  }

  def toJavaModel: JavaChange = {
    val lineList: java.util.List[JavaLine] = new util.ArrayList[JavaLine]( asJavaCollection(lines map {_.toJavaModel}))
    new JavaChange(lineList, fileName)
  }
}
