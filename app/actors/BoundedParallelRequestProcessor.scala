package actors

import akka.actor.Actor
import scala.collection.immutable.Queue
import scala.concurrent.Future
import scala.reflect.ClassTag

abstract class BoundedParallelRequestProcessor[T: ClassTag] extends Actor {

  import context.dispatcher

  def maxParallelConnections: Int

  def doRequest(request: T): Future[_]

  case object RequestFinished

  def receive() = readyForRequest(maxParallelConnections)

  def readyForRequest(allowedConnections: Int): Receive = {
    case request: T =>
      if (allowedConnections > 0) {
        proceedRequest(request)
        context become readyForRequest(allowedConnections - 1)
      }
      else context become queueingRequests(Queue(request))

    case RequestFinished =>
      println(s"resquest finished a")
      context become readyForRequest(allowedConnections + 1)
  }

  def queueingRequests(requests: Queue[T]): Receive = {
    case request: T =>
      context become queueingRequests(requests enqueue request)

    case RequestFinished =>
      println(s"resquest finished b")
      val (request, rest) = requests.dequeue
      proceedRequest(request)
      if (rest.isEmpty) context become receive()
      else context become queueingRequests(rest)
  }

  def proceedRequest(request: T): Unit = {
    val future = doRequest(request)
    future onComplete {
      case _ => self ! RequestFinished
    }
  }
}