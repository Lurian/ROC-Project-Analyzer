package master.elastic.model.response

case class Hits(total: Int, max_score: Int, hits: List[String])
