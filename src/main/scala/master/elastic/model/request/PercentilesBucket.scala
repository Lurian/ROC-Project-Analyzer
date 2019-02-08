package master.elastic.model.request

case class PercentilesBucket(buckets_path: String, percents: List[Double])
