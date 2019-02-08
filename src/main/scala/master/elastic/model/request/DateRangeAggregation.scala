package master.elastic.model.request

// Range Aggregation
case class DateRangeAggregation(date_range: DateRangeBucket, aggs: EndpointAndPercentileAggregation)
