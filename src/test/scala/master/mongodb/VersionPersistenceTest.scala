package master.mongodb

import akka.event.{LoggingAdapter, NoLogging}
import master.mongodb.version.VersionPersistence.LoadVersions
import master.mongodb.version.{VersionModel, VersionPersistence}
import org.mongodb.scala.{MongoCollection, Observer}
import org.scalamock.scalatest.MockFactory
import org.scalatest.FreeSpec


class VersionPersistenceTest extends FreeSpec with MockFactory {

  implicit val log: LoggingAdapter = NoLogging

  "An AppMethod" - {
    "should correctly extracted parameters from methodDeclaration" in {
      val mockJMC = stub[com.mongodb.async.client.MongoCollection[VersionModel]]
      val versionMongoCollectionMock: MongoCollection[VersionModel] = MongoCollection[VersionModel](mockJMC)

      val versionPersistence = VersionPersistence(versionMongoCollectionMock)

      val requestObserver = stub[Observer[VersionModel]]
      val loadRequest = LoadVersions("7", requestObserver)
      versionPersistence.load(loadRequest)
    }
  }
}