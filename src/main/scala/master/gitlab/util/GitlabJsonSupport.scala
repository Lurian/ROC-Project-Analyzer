package master.gitlab.util

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import master.gitlab.model.{CommitDTO, CompareDTO, DiffDTO, TagDTO}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait GitlabJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  import master.util.DateMarshalling._

  implicit val commitFormat: RootJsonFormat[CommitDTO] = jsonFormat12(CommitDTO)
  implicit val tagFormat: RootJsonFormat[TagDTO] = jsonFormat2(TagDTO)
  implicit val commitDiffFormat: RootJsonFormat[DiffDTO] = jsonFormat6(DiffDTO)
  implicit val compareFormat: RootJsonFormat[CompareDTO] = jsonFormat5(CompareDTO)
}
