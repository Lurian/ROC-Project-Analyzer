package master.elastic.model.request

case class DateRangeBucket(field: String, format: String, ranges: List[Map[String, String]])
