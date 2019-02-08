package master.mongodb

import akka.event.LoggingAdapter
import org.mongodb.scala.{Observable, SingleObservable}


abstract class MongoPersistence[T] ()(implicit log: LoggingAdapter) {

  protected def createWriteRequest(request: MongoRequest): SingleObservable[Any]
  protected def createLoadRequest(request: MongoRequest): Observable[T]

  protected def className: String

  /**
    * Handles a load request of a [[T]]
    * @param request Write Request
    */
  def persist(request: MongoRequest): Unit = {
    log.debug(s"$className write request received")

    // Sending request to mongo
    val mongoRequest = createWriteRequest(request)

    // Subscribing debug observers
//    mongoRequest.subscribe(MongoUtil.onWriteLogObserver[Any](className))

    // Subscribing request callback observer
    mongoRequest.subscribe(request.requestCallback)
  }

  /**
    * Handles a load request of a [[T]]
    * @param request Load Request
    */
  def load(request: MongoLoadRequest[T]): Unit = {
    log.debug(s"$className load request received")

    // Creating the mongo load request
    val mongoRequest = createLoadRequest(request)

    // Subscribing request observer
    mongoRequest.subscribe(request.observer)

    // Subscribing request callback observer
    mongoRequest.subscribe(request.requestCallback)
  }

}
