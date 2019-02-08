package master.project.task

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.event.LoggingAdapter
import master.elastic.ElasticHttpRequester
import master.elastic.ElasticHttpRequester.ElasticRequestTask
import master.elastic.model.response.{ElasticResponse, EndpointBucketAnswer}
import master.endpoint.{Endpoint, RestConfig}
import master.mongodb.MongoUtil
import master.mongodb.elastic.ElasticVersionAnalysisPersistence.PersistElasticVersionAnalysis
import master.mongodb.elastic.{ElasticVersionAnalysis, Percentile}
import master.mongodb.endpoint.EndpointImpactMapPersistence.LoadEndpointImpactMap
import master.mongodb.endpoint.EndpointImpactModel
import master.mongodb.version.VersionModel
import master.mongodb.version.VersionPersistence.LoadVersions
import master.project.ProjectConfig
import master.util.ConfigHelper.getYamlConfigFile
import org.joda.time.DateTime

object ElasticTaskAnalyzer {
  case class ElasticAnalysisRequest(versionName: String)
  case class ElasticAnalysisCompleted()

  val restConfigFile = "./src/main/resources/restConfig.yaml"
  val restConfig: RestConfig = getYamlConfigFile[RestConfig](restConfigFile, classOf[RestConfig])

  def props(projectId: String, mongoManager: ActorRef, elasticHttpRequester: ActorRef, requester: ActorRef): Props = {
    Props(new ElasticTaskAnalyzer(projectId, mongoManager, elasticHttpRequester, requester))
  }

  private def prettyPrint(endpointList: List[Endpoint]): Unit =
  endpointList foreach {(endpoint) =>  println(s"verb:${endpoint.verb} type:${endpoint.endpointType} endpoint:${endpoint.endpoint}")}

  def extractEndpoints(endpointList: List[Endpoint])(key: String): List[Endpoint] = {
    val verb: String = key.split('|')(0).toUpperCase()
    val endpoint: String = key.split('|')(1)
    // If it has more than 3 '/' its a subResource
    val endpointType: String = if(key.count(_.equals('/')) >= restConfig.getSubResourceSlashesThreshold)
      restConfig.getSubResourceTag
    else
      restConfig.getResourceTag

    // Filters Definitions
    def byVerb: Endpoint => Boolean = (listEndpoint) => listEndpoint.verb.equals(verb)
    def byType: Endpoint => Boolean =  (listEndpoint) => listEndpoint.endpointType.equals(endpointType)
    def byEndpointStringEquals: Endpoint => Boolean = (listEndpoint) => endpoint.equals(listEndpoint.endpoint)
    def byEndpointStringContains: Endpoint => Boolean = (listEndpoint) => endpoint.contains(listEndpoint.endpoint)
    def byEndpointStringReverseStartsWith: Endpoint => Boolean = (listEndpoint) =>
      endpoint.reverse.startsWith(listEndpoint.endpoint.reverse)

    // First it filters by the EndpointString checking equality
    var endpointListFilteredByString = endpointList filter byEndpointStringEquals

    // If it didn't find any fallback to contains check
    if(endpointListFilteredByString.isEmpty) endpointListFilteredByString = endpointList filter byEndpointStringContains

    // If it found only one, return it
    if(endpointListFilteredByString.size == 1) return endpointListFilteredByString

    // Then it filters by the Verb type
    val endpointListFilteredByVerb = endpointListFilteredByString filter byVerb

    // If it found only one, return it
    if(endpointListFilteredByVerb.size == 1) return endpointListFilteredByVerb

    // Then it filters by the EndpointType
    val endpointListFilteredByType = endpointListFilteredByVerb filter byType

    // If it found only one, return it
    if(endpointListFilteredByType.size == 1) return endpointListFilteredByType

    // First it filters by the ReverseEndpointStartsWith checking equality and return it
    endpointListFilteredByType filter byEndpointStringReverseStartsWith
  }
}

class ElasticTaskAnalyzer(projectId: String, mongoManager: ActorRef,
                          elasticHttpRequester: ActorRef, requester: ActorRef)
  extends Actor with ActorLogging with Stash {
  import ElasticTaskAnalyzer._
  implicit val implicitLog: LoggingAdapter = log

  val projectConfigFile = "./src/main/resources/projectConfig.yaml"
  val projectConfig: ProjectConfig = getYamlConfigFile[ProjectConfig](projectConfigFile, classOf[ProjectConfig])

  override def preStart(): Unit = {
    log.info("ElasticTaskAnalyzer started")
  }

  override def postStop(): Unit ={
    log.info("Terminating ElasticTaskAnalyzer")
  }

  def getToDateTime(toDate: Option[Date]) = {
    if (toDate.isDefined) {
      new DateTime(toDate.get).toString(ElasticHttpRequester.datePattern)
    } else {
      new DateTime().toString(ElasticHttpRequester.datePattern)
    }
  }

  def waitingForRequest: Receive = {
    case ElasticAnalysisRequest(versionName) =>
      log.debug(s"Elastic analysis request received for versionName:$versionName")
      val versionLoadRequest = LoadVersions(projectId, versionTagOption = Some(versionName), observer =  MongoUtil.aggregatorObserver[VersionModel](
        (versionModelList: List[VersionModel]) => {
          log.debug(s"VersionModel successfully loaded for versionName:$versionName")
          val endpointLoadRequest = LoadEndpointImpactMap(projectId, Some(versionName),  MongoUtil.aggregatorObserver[EndpointImpactModel](
            (endpointImpactModelList: List[EndpointImpactModel]) => {
              log.debug(s"EndpointImpactModel successfully loaded for versionName:$versionName")
              // TODO: Create a single entity mongoUtil observer
              val versionModel = versionModelList.head
              val endpointImpactModel = endpointImpactModelList.head

              val fromDateTime: String = new DateTime(versionModel.fromDate).toString(ElasticHttpRequester.datePattern)
              val toDateTime = getToDateTime(versionModel.toDate)

              log.debug("Sending request to http requester")
              elasticHttpRequester ! ElasticRequestTask(List(25.0, 50.0, 75.0), fromDateTime, toDateTime)
              context.become(waitingForElasticResponse(endpointImpactModel, versionModel))
            }))
          mongoManager ! endpointLoadRequest
        }))
      mongoManager ! versionLoadRequest
  }

  def waitingForElasticResponse(endpointImpactModel: EndpointImpactModel, versionModel: VersionModel): Receive = {
    case response@ElasticResponse(_,_,_,_,aggregations) =>
      log.debug(s"ElasticResponse received for versionName${versionModel.versionName}")
      val versionExtractEndpoint: String => List[Endpoint] = extractEndpoints(endpointImpactModel.endpointList)

      // Filtering only project requisitions - This filter only endpoints that starts with:
      // ->> projectConfig.projectRootApiCall
      // AND that endpointString does not contains:
      // ->> 'resources'
      val filteredElasticEndpoints =  aggregations.range.buckets.head.endpoints.buckets filter { (bucket) =>
        // Extracting endpoint string from <verb>|<endpoint> string pattern
        val endpoint: String = bucket.key.split('|')(1)
        val startsWithApiCall: Boolean = endpoint.startsWith(projectConfig.projectRootApiCall)
        val haveRelevance: Boolean = !endpoint.contains("resources")
        startsWithApiCall && haveRelevance
      }

      // Mapping EndpointString removing the projectConfig.projectRootApiCall from the beginning and putting it into lowercase
      val mappedElasticEndpoints: List[EndpointBucketAnswer] = filteredElasticEndpoints map {
        (bucket) =>
          val mappedEndpointString = bucket.key.replace(projectConfig.projectRootApiCall, "").toLowerCase()
          EndpointBucketAnswer(mappedEndpointString, bucket.doc_count)
      }

      val restOptionEndpointMap: Map[Option[Endpoint], Int] = mappedElasticEndpoints map {
        (aggregationBucket) => {
          val endpointList = versionExtractEndpoint(aggregationBucket.key)
          if(endpointList.size > 1) {
            log.debug(s"More than one endpoint returned for ${aggregationBucket.key}")
          }
          if(endpointList.isEmpty){
            val filtered = endpointImpactModel.endpointList filter {_.endpoint.contains("peca")}
            log.error(s"No endpoint found for ${aggregationBucket.key}")
            None -> aggregationBucket.doc_count
          } else {
            Some(endpointList.head) -> aggregationBucket.doc_count
          }
        }
      } toMap

      val percentileMap: Map[String, Option[Int]] = aggregations.range.buckets.head.percentiles_count.values
      val percentileList: List[Percentile] = percentileMap map {(kv) => Percentile(kv._1, kv._2)} toList
      val restEndpointList: List[Endpoint] = restOptionEndpointMap filter {_._1.isDefined} map
        {(kv) => kv._1.get.addUsage(Some(kv._2))} toList
      val elasticVersionAnalysis = ElasticVersionAnalysis(projectId, versionModel.versionName, percentileList, restEndpointList)
      mongoManager ! PersistElasticVersionAnalysis(elasticVersionAnalysis, callback = () => requester ! ElasticAnalysisCompleted())
      context.stop(self)
  }

  override def receive = waitingForRequest

  def groupByPercentile(restEndpointMap:  Map[Endpoint, Int])
                       (percentileRange: (Option[Double], Option[Double])):  List[Endpoint] = {
    val from = percentileRange._1
    val to = percentileRange._2
    if(from.isEmpty && to.isEmpty) return restEndpointMap.keys.toList
    if(to.isEmpty) return (restEndpointMap filter {(kv) => kv._2 >= from.get}).keys.toList
    if(from.isEmpty) return (restEndpointMap filter {(kv) => kv._2 < to.get}).keys.toList
    (restEndpointMap filter {(kv) => kv._2 >= from.get &&  kv._2 < from.get}).keys.toList
  }
}