package master.project

import akka.actor.{ActorRef, ActorRefFactory}
import master.project.ProjectActorFactory.ProjectActorFactoryConfig
import master.project.ProjectManager.ManagerConfig
import master.project.task._
import master.util.SourceUtil

class ProjectActorConfig(projectConfig: ManagerConfig, projectId: String, mongoManager: ActorRef,
                         gitlabManager: ActorRef, projectMangerRef: ActorRef) {
  implicit val implicitProjectManagerRef: ActorRef = projectMangerRef

  def createFactoryConfig(context: ActorRefFactory,
                          bugAnalysisManagerFactory: ActorRefFactory => ActorRef = bugAnalysisManagerFactory,
                          changeAnalysisManagerFactory: ActorRefFactory => ActorRef = changeAnalysisManagerFactory,
                          versionAnalysisManagerFactory: ActorRefFactory => ActorRef = versionAnalysisManagerFactory,
                          initLoadManagerFactory: ActorRefFactory => ActorRef = initLoadManagerFactory,
                          coverageAnalysisManagerFactory: ActorRefFactory => ActorRef = coverageAnalysisManagerFactory,
                          logAnalyzerManagerFactory: ActorRefFactory => ActorRef = logAnalyzerManagerFactory,
                          endpointAnalyzerManagerFactory: ActorRefFactory => ActorRef = endpointAnalyzerManagerFactory,
                          elasticManagerFactory: ActorRefFactory => ActorRef = elasticManagerFactory,
                          researchManagerFactory: ActorRefFactory => ActorRef = researchManagerFactory): ProjectActorFactoryConfig =
    ProjectActorFactoryConfig(context, bugAnalysisManagerFactory, changeAnalysisManagerFactory, versionAnalysisManagerFactory,
      initLoadManagerFactory, coverageAnalysisManagerFactory, logAnalyzerManagerFactory, endpointAnalyzerManagerFactory,
      elasticManagerFactory, researchManagerFactory)

  def bugAnalysisManagerFactory(implicit self: ActorRef): ActorRefFactory => ActorRef = (context: ActorRefFactory) =>
    context.actorOf(BugAnalysisTaskManager.props(projectId, mongoManager, gitlabManager, self), s"BugAnalysisManager")

  def changeAnalysisManagerFactory(implicit projectMangerRef: ActorRef): ActorRefFactory => ActorRef = (context: ActorRefFactory) =>
    context.actorOf(ChangeAnalysisTaskManager.props(projectId, mongoManager, gitlabManager, projectMangerRef), s"VersionAnalysisManager-$projectId")

  def versionAnalysisManagerFactory(implicit projectMangerRef: ActorRef): ActorRefFactory => ActorRef = (context: ActorRefFactory) =>
    context.actorOf(VersionAnalysisTaskManager.props(projectId, mongoManager, gitlabManager, projectMangerRef), s"InitLoadManager-$projectId")

  def initLoadManagerFactory(implicit projectMangerRef: ActorRef): ActorRefFactory => ActorRef = (context: ActorRefFactory) =>
    context.actorOf(InitDataTaskManager.props(projectId, mongoManager, gitlabManager, projectMangerRef), s"ChangeAnalysisManager-$projectId")

  def coverageAnalysisManagerFactory(implicit projectMangerRef: ActorRef): ActorRefFactory => ActorRef = (context: ActorRefFactory) =>
    context.actorOf(CoverageAnalysisTaskManager.props(projectId, mongoManager, projectMangerRef), s"CoverageAnalysisManager-$projectId")

  def logAnalyzerManagerFactory(implicit projectMangerRef: ActorRef): ActorRefFactory => ActorRef = (context: ActorRefFactory) =>
    context.actorOf(LogAnalysisTaskManager.props(projectId, mongoManager, projectMangerRef), s"LogAnalysisManager-$projectId")

  def endpointAnalyzerManagerFactory(implicit projectMangerRef: ActorRef): ActorRefFactory => ActorRef = (context: ActorRefFactory) =>
    context.actorOf(EndpointAnalysisTaskManager.props(projectId, mongoManager, projectConfig.gitlabConfig, projectMangerRef), s"EndpointAnalysisManager-$projectId")

  def elasticManagerFactory(implicit projectMangerRef: ActorRef): ActorRefFactory => ActorRef = (context: ActorRefFactory) =>
    context.actorOf(ElasticTaskManager.props(projectId, mongoManager, projectMangerRef), s"ElasticManager-$projectId")

  def researchManagerFactory(implicit projectMangerRef: ActorRef): ActorRefFactory => ActorRef = (context: ActorRefFactory) =>
    context.actorOf(ResearchUnitManager.props(projectId,
      ResearchUnitManager.versionUnitManagerFactory(projectId, mongoManager, gitlabManager, SourceUtil),
      ResearchUnitManager.methodUnitManagerFactory(projectId, mongoManager, gitlabManager, SourceUtil),
      ResearchUnitManager.defaultNumberOfWorkers, mongoManager, gitlabManager, projectMangerRef, SourceUtil),
      s"ResearchManager-$projectId")
}
