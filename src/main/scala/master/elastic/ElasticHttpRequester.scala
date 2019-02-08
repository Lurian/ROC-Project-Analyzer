package master.elastic

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.StatusCodes.ServerError
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import master.elastic.model.ElasticJsonSupport
import master.elastic.model.request._
import master.elastic.model.response._
import master.gitlab.request.GitlabRequest.RequestError
import master.gitlab.request.util.RequestTimeout

import scala.util.{Failure, Success}

object ElasticHttpRequester {
  def datePattern = "yyyy-MM-dd:HH:mm:ss.SSS-Z"
  case class ElasticRequestTask(percentileList: List[Double],
                                fromDateTime: String,
                                toDateTime: String)
  def props(): Props = {
    Props(new ElasticHttpRequester())
  }
}

class ElasticHttpRequester() extends Actor
  with ActorLogging with Stash with ElasticJsonSupport{
  import akka.pattern.pipe
  import context.dispatcher
  import ElasticHttpRequester._
  val elasticURI = "http://localhost:9200/logstash-*/_search?size=0"
//  2016-11-08T14:43:33.000-03:00

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))
  val http = Http(context.system)

  override def preStart(): Unit = {
    log.info("ElasticHttpRequester initialized")
  }

  override def postStop(): Unit = {
    log.info("Terminating ElasticHttpRequester")
  }

  private def createRequest(percentileList: List[Double], fromDateTime: String, toDateTime: String) = {
    val endpointBucket = TermAggregation(TermBucket(ScriptField(), 200))
    val percentilePipeline = PercentilePipeline(PercentilesBucket("endpoints>_count", percentileList))
    val endpointAndPercentileAgg = EndpointAndPercentileAggregation(endpointBucket, percentilePipeline)

    val dateRangeBucket = DateRangeBucket("@timestamp", datePattern, List(Map("from" -> fromDateTime, "to" -> toDateTime)))
    val dateRangeAggregation = DateRangeAggregation(dateRangeBucket, endpointAndPercentileAgg)
    val myAggregation = MyAggregation(dateRangeAggregation)

    ElasticRequest(myAggregation)
  }

  private def doRequest(percentileList: List[Double] = List(25.0, 50.0, 75.0),
                fromDateTime: String = "2017-05-24 15:09:44",
                toDateTime: String = "2017-05-31 20:22:16") = {
    val elasticRequest = createRequest(percentileList, fromDateTime, toDateTime)

    Marshal(elasticRequest).to[RequestEntity] flatMap { entity =>
      http.singleRequest(HttpRequest(uri = elasticURI, method = HttpMethods.POST, entity = entity)
      ).pipeTo(self)
    }
  }
  private def waitingForRequest: Receive = {
    case ElasticRequestTask(percentileList, fromDateTime, toDateTime) =>
      log.debug(s"ElasticRequestTask received fromDateTime:$fromDateTime / toDateTime:$toDateTime ")
      doRequest(percentileList, fromDateTime, toDateTime)
      context.become(waitingForResponse(sender()))
    case other@_ =>
      log.debug(s"other:$other")
  }

  private def waitingForResponse(requester: ActorRef): Receive = {
    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      val result = Unmarshal(entity).to[ElasticResponse]
      result onComplete {
        case Success(value: ElasticResponse) =>
          log.debug("Successful unarmshalling")
          requester ! value
        case Failure(err) =>
          throw err
      }
      unstashAll()
      context.become(waitingForRequest)
    case HttpResponse(code, _, entity, _) =>
      log.error(s"Request failed, response code: $code, $entity")
      entity.dataBytes.runWith(Sink.ignore)
      requester ! RequestError("")
      unstashAll()
      context.become(waitingForRequest)
    case _ =>
      stash()
  }

  def receive = waitingForRequest
}