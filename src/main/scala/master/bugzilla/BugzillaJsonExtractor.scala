package master.bugzilla

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import master.util.FileSource

object BugzillaJsonExtractor {
  def extract(bugSource: FileSource): List[BugDTO] = {
    val mapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)

    val x: Map[String, List[BugDTO]] = mapper.readValue[Map[String, List[BugDTO]]](bugSource.mkString)

    (x.values flatten) toList
  }
}
