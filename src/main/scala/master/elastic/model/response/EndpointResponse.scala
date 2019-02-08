package master.elastic.model.response

// Endpoint Bucket
case class EndpointResponse(doc_count_error_upper_bound: Int, sum_other_doc_count: Int, buckets: List[EndpointBucketAnswer])
