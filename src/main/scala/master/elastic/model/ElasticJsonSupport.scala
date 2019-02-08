package master.elastic.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import master.elastic.model.request._
import master.elastic.model.response._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait ElasticJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  // Request
  implicit val ScriptFieldFormat: RootJsonFormat[ScriptField] = jsonFormat2(ScriptField)
  implicit val TermBucketFormat: RootJsonFormat[TermBucket] = jsonFormat2(TermBucket)
  implicit val PercentilesBucketFormat: RootJsonFormat[PercentilesBucket] = jsonFormat2(PercentilesBucket)
  implicit val DateRangeBucketFormat: RootJsonFormat[DateRangeBucket] = jsonFormat3(DateRangeBucket)


  implicit val TermAggregationFormat: RootJsonFormat[TermAggregation] = jsonFormat1(TermAggregation)
  implicit val PercentilePipelineFormat: RootJsonFormat[PercentilePipeline] = jsonFormat1(PercentilePipeline)
  implicit val EndpointAndPercentileAggregationFormat: RootJsonFormat[EndpointAndPercentileAggregation] = jsonFormat2(EndpointAndPercentileAggregation)

  implicit val DateRangeAggregationFormat: RootJsonFormat[DateRangeAggregation] = jsonFormat2(DateRangeAggregation)
  implicit val MyAggregationFormat: RootJsonFormat[MyAggregation] = jsonFormat1(MyAggregation)

  implicit val ElasticRequestFormat: RootJsonFormat[ElasticRequest] = jsonFormat1(ElasticRequest)

  // Response
  implicit val PercentilesBucketAnswerFormat: RootJsonFormat[PercentilesBucketAnswer] = jsonFormat1(PercentilesBucketAnswer)

  implicit val EndpointBucketAnswerFormat: RootJsonFormat[EndpointBucketAnswer] = jsonFormat2(EndpointBucketAnswer)
  implicit val EndpointResponseFormat: RootJsonFormat[EndpointResponse] = jsonFormat3(EndpointResponse)

  implicit val RangeBucketResponseFormat: RootJsonFormat[RangeBucketResponse] = jsonFormat8(RangeBucketResponse)
  implicit val BucketResponseFormat: RootJsonFormat[BucketResponse] = jsonFormat1(BucketResponse)

  implicit val MyAggregationResponseFormat: RootJsonFormat[MyAggregationResponse] = jsonFormat1(MyAggregationResponse)
  implicit val HitsFormat: RootJsonFormat[Hits] = jsonFormat3(Hits)
  implicit val ShardsFormat: RootJsonFormat[Shards] = jsonFormat4(Shards)

  implicit val ElasticResponseFormat: RootJsonFormat[ElasticResponse] = jsonFormat5(ElasticResponse)
}
