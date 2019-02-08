package master.mongodb

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import com.mongodb.bulk.BulkWriteResult
import master.gitlab.util.Constants
import org.mongodb.scala.Observer
import org.mongodb.scala.result.UpdateResult

object MongoUtil {

  def aggregatorObserver[T](onCompleteFunc : (List[T]) => Unit)(implicit log: LoggingAdapter): Observer[T] = new Observer[T] {
    var list : List[T] = List.empty
    override def onNext(result: T): Unit = list = list ++ List(result)
    override def onError(e: Throwable): Unit = {log.error(cause = e, e.toString); throw e}
    override def onComplete(): Unit = {
      try onCompleteFunc(list)
      catch {
        case ex: Exception =>
          log.error(ex.toString)
      }
    }
  }

  def singleValueObserver[T](onCompleteFunc : (T) => Unit)(implicit log: LoggingAdapter): Observer[T] = new Observer[T] {
    var result : T = _
    override def onNext(next: T): Unit = result = next
    override def onError(e: Throwable): Unit = {log.error(cause = e, e.toString); throw e}
    override def onComplete(): Unit = {
      try onCompleteFunc(result)
      catch {
        case ex: Exception =>
          log.error(ex.toString)
      }
    }
  }

  def onWriteLogObserver[T](className: String)(implicit log: LoggingAdapter): Observer[T] = new Observer[T] {
    override def onNext(result: T): Unit = {}
    override def onError(err: Throwable): Unit = {log.error(err.getMessage)}
    override def onComplete(): Unit = if(Constants.MONGO_LOG_IT) log.debug(s"$className write request complete")
  }

  def sendBackObserver[T](actorRef: ActorRef)(implicit log: LoggingAdapter): Observer[T] = new Observer[T] {
    var list: List[T] = List.empty
    override def onNext(result: T): Unit = list = list ++ List(result)
    override def onError(e: Throwable): Unit = {log.error(cause = e, e.toString); throw e }
    override def onComplete(): Unit = actorRef ! list
  }
}
