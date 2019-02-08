package master.mongodb

import org.mongodb.scala.Observer

class MongoLoadRequest[T](_observer: Observer[T], callback: () => Any) extends MongoRequest(callback) {
  def observer: Observer[T] = _observer
}
