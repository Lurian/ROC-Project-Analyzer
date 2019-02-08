package master.elastic.model.request

case class ScriptField(source: String = "doc['verb.keyword'].value + '|' + doc['endpoint.keyword'].value",
                       lang: String = "painless")
