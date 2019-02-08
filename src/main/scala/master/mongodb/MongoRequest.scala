package master.mongodb

import org.mongodb.scala.Observer

/**
  * Companion object of [[MongoRequest]]
  */
object MongoRequest {
  def createOnCompletionObserver(callback: () => Any): Observer[Any] = new Observer[Any] {
    override def onNext(result: Any): Unit = {}
    override def onError(e: Throwable): Unit = {}
    override def onComplete(): Unit = { callback()}
  }
  def doNothing : (() => Any) = () => {}
}

/**
  * Abstract class defining a way to append a callback on completion.
  * All classes representing mongo request should extends this class.
  */
abstract class MongoRequest(callback: () => Any) {
  /** Callback function. Should be overwritten when needed. */
  def requestCallback: Observer[Any] = MongoRequest.createOnCompletionObserver(callback)
}