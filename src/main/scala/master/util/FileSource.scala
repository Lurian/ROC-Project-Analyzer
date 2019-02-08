package master.util

import scala.io.Source

case class FileSource(source: Source) {
  def getLines: Iterator[String] = source.getLines()

  def mkString: String = source.mkString
}
