package master.mongodb

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingAdapter
import com.mongodb.ConnectionString
import com.mongodb.connection.ConnectionPoolSettings
import master.endpoint.{AppMethod, Endpoint}
import master.javaParser.model.Impact
import master.log.{EndpointEntry, LogLine}
import master.mongodb.bug.BugPersistence.{LoadBugs, PersistBugs}
import master.mongodb.bug.{BugModel, BugPersistence}
import master.mongodb.commit.CommitImpactPersistence.{LoadCommitImpact, PersistCommitImpact}
import master.mongodb.commit.CommitPersistence.{LoadCommits, LoadCommitsIn, PersistCommits}
import master.mongodb.commit.{CommitImpact, CommitImpactPersistence, CommitModel, CommitPersistence}
import master.mongodb.coverage.CoveragePersistence.{LoadCoverage, PersistCoverage}
import master.mongodb.coverage._
import master.mongodb.diff.DiffPersistence.{LoadDiff, LoadDiffsIn, PersistDiffs}
import master.mongodb.diff.{DiffModel, DiffPersistence}
import master.mongodb.elastic.ElasticVersionAnalysisPersistence.{LoadElasticVersionAnalysis, PersistElasticVersionAnalysis}
import master.mongodb.elastic.{ElasticVersionAnalysis, ElasticVersionAnalysisPersistence, Percentile}
import master.mongodb.endpoint.EndpointImpactMapPersistence.{LoadEndpointImpactMap, PersistEndpointImpactMap}
import master.mongodb.endpoint.{EndpointImpactMapPersistence, EndpointImpactModel}
import master.mongodb.file.FilePersistence.{LoadFile, PersistFile}
import master.mongodb.file.{FileModel, FilePersistence}
import master.mongodb.log.LogPersistence.{LoadLogs, PersistLogs}
import master.mongodb.log.{LogModel, LogPersistence}
import master.mongodb.tag.TagPersistence.{LoadTags, PersistTags}
import master.mongodb.tag.{TagModel, TagPersistence}
import master.mongodb.version.VersionImpactPersistence.{LoadVersionImpact, PersistVersionImpact}
import master.mongodb.version.VersionPersistence.{LoadVersions, PersistVersions}
import master.mongodb.version.{VersionImpact, VersionImpactPersistence, VersionModel, VersionPersistence}
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.connection.ClusterSettings
import org.mongodb.scala.{MongoClient, MongoClientSettings, MongoDatabase}

object MongoManager {
  def props(mongoConfig: MongoConfig): Props = {
    Props(new MongoManager(mongoConfig))
  }
}

class MongoManager(mongoConfig: MongoConfig) extends Actor with ActorLogging {
  var mongoClient: MongoClient = _
  var tagPersistence:  TagPersistence = _
  var commitPersistence:  CommitPersistence = _
  var diffPersistence: DiffPersistence = _
  var filePersistence: FilePersistence = _
  var versionPersistence: VersionPersistence = _
  var commitImpactPersistence: CommitImpactPersistence = _
  var versionImpactPersistence: VersionImpactPersistence = _
  var bugPersistence: BugPersistence = _
  var coveragePersistence: CoveragePersistence = _
  var logPersistence: LogPersistence = _
  var endpointPersistence: EndpointImpactMapPersistence = _
  var elasticPersistence: ElasticVersionAnalysisPersistence = _

  override def preStart(): Unit = {
    implicit val implicitLog: LoggingAdapter = log
    log.info("MongoManager started")
    // Codec registry
    val codecRegistry = fromRegistries(
      fromProviders(classOf[TagModel]),
      fromProviders(classOf[CommitModel]),
      fromProviders(classOf[DiffModel]),
      fromProviders(classOf[VersionModel]),
      fromProviders(classOf[CommitImpact]),
      fromProviders(classOf[VersionImpact]),
      fromProviders(classOf[Impact]),
      fromProviders(classOf[FileModel]),
      fromProviders(classOf[BugModel]),
      fromProviders(classOf[BundleCoverage]),
      fromProviders(classOf[CoverageModel]),
      fromProviders(classOf[MethodCoverage]),
      fromProviders(classOf[PackageCoverage]),
      fromProviders(classOf[ClassCoverage]),
      fromProviders(classOf[CoverageCounter]),
      fromProviders(classOf[LogModel]),
      fromProviders(classOf[LogLine]),
      fromProviders(classOf[EndpointEntry]),
      fromProviders(classOf[AppMethod]),
      fromProviders(classOf[Endpoint]),
      fromProviders(classOf[EndpointImpactModel]),
      fromProviders(classOf[Percentile]),
      fromProviders(classOf[ElasticVersionAnalysis]),
      DEFAULT_CODEC_REGISTRY)

    // Mongo client configuration and creation
    val connectionPoolSettings: ConnectionPoolSettings = ConnectionPoolSettings.builder().maxWaitQueueSize(2000).build()
    val clusterSettings: ClusterSettings = ClusterSettings.builder().applyConnectionString(new ConnectionString(mongoConfig.connectionString)).build()
    val settings: MongoClientSettings = MongoClientSettings.builder().connectionPoolSettings(connectionPoolSettings).clusterSettings(clusterSettings).build()
    this.mongoClient = MongoClient(settings)
    val database: MongoDatabase = mongoClient.getDatabase(mongoConfig.databaseName).withCodecRegistry(codecRegistry)

    // Creating persistence objects
    this.tagPersistence = TagPersistence(database.getCollection("tag"))
    this.commitPersistence = CommitPersistence(database.getCollection("commit"))
    this.diffPersistence = DiffPersistence(database.getCollection("diff"))
    this.filePersistence = FilePersistence(database.getCollection("file"))
    this.versionPersistence = VersionPersistence(database.getCollection("version"))
    this.commitImpactPersistence = CommitImpactPersistence(database.getCollection("commitImpact"))
    this.versionImpactPersistence = VersionImpactPersistence(database.getCollection("versionImpact"))
    this.bugPersistence = BugPersistence(database.getCollection("bug"))
    this.coveragePersistence = CoveragePersistence(database.getCollection("coverage"))
    this.logPersistence = LogPersistence(database.getCollection("log"))
    this.endpointPersistence = EndpointImpactMapPersistence(database.getCollection("endpoint"))
    this.elasticPersistence = ElasticVersionAnalysisPersistence(database.getCollection("elastic"))
  }

  override def postStop(): Unit = {
    log.warning("Mongo Manager shutting down - MongoClient connection closing...")
    mongoClient.close()
  }

  override def receive = {
    // TagPersistence requests
    case req@LoadTags(_, _, _) =>
      tagPersistence.load(req)
    case req@PersistTags(_, _) =>
      tagPersistence.persist(req)

    // CommitPersistence requests
    case req@LoadCommits(_, _, _, _) =>
      commitPersistence.load(req)
    case req@LoadCommitsIn(_, _, _, _) =>
      commitPersistence.load(req)
    case req@PersistCommits(_, _) =>
      commitPersistence.persist(req)

    // DiffPersistence requests
    case req@LoadDiff(_, _, _, _) =>
      diffPersistence.load(req)
    case req@LoadDiffsIn(_, _, _, _) =>
      diffPersistence.load(req)
    case req@PersistDiffs(_, _) =>
      diffPersistence.persist(req)

    // VersionPersistence requests
    case req@LoadVersions(_, _, _, _) =>
      versionPersistence.load(req)
    case req@PersistVersions(_, _) =>
      versionPersistence.persist(req)

    // FilePersistence requests
    case req@LoadFile(_, _, _, _, _) =>
      filePersistence.load(req)
    case req@PersistFile(_, _) =>
      filePersistence.persist(req)

    // CommitImpactPersistence requests
    case req@LoadCommitImpact(_, _, _, _) =>
      commitImpactPersistence.load(req)
    case req@PersistCommitImpact(_, _) =>
      commitImpactPersistence.persist(req)

    // VersionImpactPersistence requests
    case req@LoadVersionImpact(_, _, _, _) =>
      versionImpactPersistence.load(req)
    case req@PersistVersionImpact(_, _) =>
      versionImpactPersistence.persist(req)

    // BugPersistence requests
    case req@LoadBugs(_, _, _) =>
      bugPersistence.load(req)
    case req@PersistBugs(_, _) =>
      bugPersistence.persist(req)

    // CoveragePersistence requests
    case req@LoadCoverage(_, _, _, _) =>
      coveragePersistence.load(req)
    case req@PersistCoverage(_, _) =>
      coveragePersistence.persist(req)

    // LogPersistence requests
    case req@LoadLogs(_, _, _) =>
      logPersistence.load(req)
    case req@PersistLogs(_, _) =>
      logPersistence.persist(req)

    // EndpointPersistence requests
    case req@LoadEndpointImpactMap(_, _, _, _) =>
      endpointPersistence.load(req)
    case req@PersistEndpointImpactMap(_, _) =>
      endpointPersistence.persist(req)

    // ElasticPersistence requests
    case req@LoadElasticVersionAnalysis(_, _, _, _) =>
      elasticPersistence.load(req)
    case req@PersistElasticVersionAnalysis(_, _) =>
      elasticPersistence.persist(req)

    case req@_ =>
      log.error(s"Mongo manager don't know how to delegate this request:$req")
  }
}