package master.project

import akka.actor.{ActorRef, ActorRefFactory}

object ProjectActorFactory {

  case class ProjectActorFactoryConfig(context: ActorRefFactory,
                                       bugAnalysisManagerFactory: ActorRefFactory => ActorRef,
                                       changeAnalysisManagerFactory: ActorRefFactory => ActorRef,
                                       versionAnalysisManagerFactory: ActorRefFactory => ActorRef,
                                       initLoadManagerFactory: ActorRefFactory => ActorRef,
                                       coverageAnalysisManagerFactory: ActorRefFactory => ActorRef,
                                       logAnalyzerManagerFactory: ActorRefFactory => ActorRef,
                                       endpointAnalyzerManagerFactory: ActorRefFactory => ActorRef,
                                       elasticManagerFactory: ActorRefFactory => ActorRef,
                                       researchManagerFactory: ActorRefFactory => ActorRef)

  def apply(context: ActorRefFactory,
            bugAnalysisManagerFactory: ActorRefFactory => ActorRef,
            changeAnalysisManagerFactory: ActorRefFactory => ActorRef,
            versionAnalysisManagerFactory: ActorRefFactory => ActorRef,
            initLoadManagerFactory: ActorRefFactory => ActorRef,
            coverageAnalysisManagerFactory: ActorRefFactory => ActorRef,
            logAnalyzerManagerFactory: ActorRefFactory => ActorRef,
            endpointAnalyzerManagerFactory: ActorRefFactory => ActorRef,
            elasticManagerFactory: ActorRefFactory => ActorRef,
            researchManagerFactory: ActorRefFactory => ActorRef): ProjectActorFactoryConfig =
    ProjectActorFactoryConfig(context, bugAnalysisManagerFactory, changeAnalysisManagerFactory, versionAnalysisManagerFactory,
      initLoadManagerFactory, coverageAnalysisManagerFactory, logAnalyzerManagerFactory, endpointAnalyzerManagerFactory,
      elasticManagerFactory, researchManagerFactory)

  def unapply(projectActorFactory: ProjectActorFactoryConfig):
  Option[(ActorRef, ActorRef, ActorRef, ActorRef, ActorRef, ActorRef, ActorRef, ActorRef, ActorRef)] = {
    val bugAnalysisManager: ActorRef = projectActorFactory.bugAnalysisManagerFactory(projectActorFactory.context)
    val changeAnalysisManager: ActorRef = projectActorFactory.changeAnalysisManagerFactory(projectActorFactory.context)
    val versionAnalysisManager: ActorRef = projectActorFactory.versionAnalysisManagerFactory(projectActorFactory.context)
    val initLoadManager: ActorRef = projectActorFactory.initLoadManagerFactory(projectActorFactory.context)
    val coverageAnalysisManager: ActorRef = projectActorFactory.coverageAnalysisManagerFactory(projectActorFactory.context)
    val logAnalyzerManager: ActorRef = projectActorFactory.logAnalyzerManagerFactory(projectActorFactory.context)
    val endpointAnalyzerManager: ActorRef = projectActorFactory.endpointAnalyzerManagerFactory(projectActorFactory.context)
    val elasticManager: ActorRef = projectActorFactory.elasticManagerFactory(projectActorFactory.context)
    val researchManager: ActorRef = projectActorFactory.researchManagerFactory(projectActorFactory.context)

    Some((bugAnalysisManager, changeAnalysisManager, versionAnalysisManager, initLoadManager, coverageAnalysisManager,
      logAnalyzerManager, endpointAnalyzerManager, elasticManager, researchManager))
  }
}
