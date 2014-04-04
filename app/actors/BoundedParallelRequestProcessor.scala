package actors

import akka.actor.{ActorLogging, ActorRef, Actor}
import scala.collection.immutable.Queue
import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * restricts number of parallel requests by putting new request to the queue until it's able to be proceeded
 */
abstract class BoundedParallelRequestProcessor[T: ClassTag] extends Actor with ActorLogging {

  import context.dispatcher

  def maxParallelConnections: Int

  def doRequest(request: T, originalSender: ActorRef): Future[_]

  private case object RequestFinished

  def receive() = readyForRequest(maxParallelConnections)

  def readyForRequest(allowedConnections: Int): Receive = {
    case request: T =>
      if (allowedConnections > 0) {
        proceedRequest(sender, request)
        context become readyForRequest(allowedConnections - 1)
      }
      else context become queueingRequests(Queue(sender -> request))

    case RequestFinished =>
      log.debug("request finished, await new request")
      context become readyForRequest(allowedConnections + 1)
  }

  def queueingRequests(requests: Queue[(ActorRef, T)]): Receive = {
    case request: T =>
      context become queueingRequests(requests enqueue (sender -> request))

    case RequestFinished =>
      log.debug("request finished, proceed request from the queue")
      val ((originalSender, request), rest) = requests.dequeue
      proceedRequest(originalSender, request)
      if (rest.isEmpty) context become receive()
      else context become queueingRequests(rest)
  }

  def proceedRequest(originalSender: ActorRef, request: T): Unit =
    doRequest(request, originalSender) onComplete {
      case _ => self ! RequestFinished
    }
}