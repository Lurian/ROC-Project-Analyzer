package master.elastic.model.response

// Answer Object
case class ElasticResponse(took: Int, timed_out: Boolean, _shards: Shards, hits: Hits, aggregations: MyAggregationResponse)
