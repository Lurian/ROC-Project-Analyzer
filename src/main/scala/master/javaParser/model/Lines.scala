package master.javaParser.model

import master.javaParser.JavaLine

case class Lines(start: Int, end: Int) {

  def toJavaModel: JavaLine = new JavaLine(start, end)
}
