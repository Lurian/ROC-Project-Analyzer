package master.elastic.model.response

case class RangeBucketResponse(key: String, from: Int, from_as_string: String, to: Int, to_as_string: String,
                               doc_count: Int, endpoints: EndpointResponse, percentiles_count: PercentilesBucketAnswer)
