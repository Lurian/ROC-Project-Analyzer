package master.elastic.model.request

// My Inner Aggregation
case class EndpointAndPercentileAggregation(endpoints: TermAggregation, percentiles_count: PercentilePipeline)
