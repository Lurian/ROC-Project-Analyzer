package master.project.task

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.event.LoggingAdapter
import master.elastic.ElasticHttpRequester
import master.mongodb.MongoUtil
import master.mongodb.endpoint.EndpointImpactMapPersistence.LoadEndpointImpactMap
import master.mongodb.endpoint.EndpointImpactModel
import master.project.task.ElasticTaskAnalyzer.ElasticAnalysisRequest

object ElasticTaskManager {
  case class ElasticAnalysisTask(versionName: Option[String] = None)
  def props(projectId: String, mongoManager: ActorRef, projectManager: ActorRef): Props = {
    Props(new ElasticTaskManager(projectId, mongoManager, projectManager))
  }
}

class ElasticTaskManager(projectId: String, mongoManager: ActorRef, projectManager: ActorRef)
  extends Actor with ActorLogging with Stash {
  import ElasticTaskManager._
  implicit val implicitLog: LoggingAdapter = log

  var elasticHttpRequester: ActorRef = context.actorOf(ElasticHttpRequester.props(),
    s"ElasticHttpRequester")

  override def preStart(): Unit = {
    log.info("ElasticTaskManager started")
  }

  override def postStop(): Unit ={
    log.info("Terminating ElasticTaskManager")
  }

  def receive: Receive = {
    case ElasticAnalysisTask(Some(versionName)) =>
      context.actorOf(ElasticTaskAnalyzer.props(projectId, mongoManager, elasticHttpRequester, projectManager),
        s"ElasticTaskAnalyzer-$versionName") ! ElasticAnalysisRequest(versionName)
    case ElasticAnalysisTask(None) =>
      mongoManager ! LoadEndpointImpactMap(projectId, observer = MongoUtil.aggregatorObserver[EndpointImpactModel]((endpointImpactModels) => {
        endpointImpactModels foreach { (endpointImpactModel) =>
          val versionName = endpointImpactModel.versionName
          context.actorOf(ElasticTaskAnalyzer.props(projectId, mongoManager, elasticHttpRequester, projectManager),
            s"ElasticTaskAnalyzer-$versionName") ! ElasticAnalysisRequest(versionName)
        }
      }))
  }
}