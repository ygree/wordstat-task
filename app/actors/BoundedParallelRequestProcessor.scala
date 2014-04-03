package actors

import akka.actor.{ActorRef, Actor}
import scala.collection.immutable.Queue
import scala.concurrent.Future
import scala.reflect.ClassTag

abstract class BoundedParallelRequestProcessor[T: ClassTag] extends Actor {

  import context.dispatcher

  def maxParallelConnections: Int

  def doRequest(request: T, originalSender: ActorRef): Future[_]

  case class RequestFinished(originalSender: ActorRef)

  def receive() = readyForRequest(maxParallelConnections)

  def readyForRequest(allowedConnections: Int): Receive = {
    case request: T =>
      if (allowedConnections > 0) {
        proceedRequest(request, sender)
        context become readyForRequest(allowedConnections - 1)
      }
      else context become queueingRequests(Queue(request))

    case RequestFinished(originalSender) =>
      println(s">>>>>>>>>request finished, await new request")
      context become readyForRequest(allowedConnections + 1)
  }

  def queueingRequests(requests: Queue[T]): Receive = {
    case request: T =>
      context become queueingRequests(requests enqueue request)

    case RequestFinished(originalSender) =>
      println(s">>>>>>>>>request finished, proceed request from the queue")
      val (request, rest) = requests.dequeue
      //TODO here is not original sender rather this actor itself
      proceedRequest(request, originalSender)
      if (rest.isEmpty) context become receive()
      else context become queueingRequests(rest)
  }

  def proceedRequest(request: T, originalSender: ActorRef): Unit = {
    val future = doRequest(request, originalSender)
    val msg = RequestFinished(originalSender)
    future onComplete {
      case _ => self ! msg
    }
  }
}