package actors

import akka.actor.{Props, ReceiveTimeout, ActorRef, Actor}
import java.net.URI
import scala.concurrent.duration.Duration

object ResponseAggregator {

  case class Request(keywords: Set[String])
  case class AggregatedResult(uris: Set[URI])

  def apply(requestProcessor: ActorRef, timeout: Duration) = Props(
    new ResponseAggregator(requestProcessor, timeout)
  )
}

class ResponseAggregator(requestProcessor: ActorRef, timeout: Duration) extends Actor {

  import ResponseAggregator._

  def receive = {
    case Request(keywords) =>
      keywords foreach { keyword =>
        requestProcessor ! YandexBlogSearcher.Search(keyword)
      }
      context become awaitResults(keywords.size, Set(), sender)
      context.setReceiveTimeout(timeout)
  }

  def awaitResults(left: Int, result: Set[URI] = Set(), respondTo: ActorRef): Receive = {
    case YandexBlogSearcher.Found(links) =>
      val newLeft = left - 1
      val newResult = result ++ links
      if (newLeft > 0) context become awaitResults(newLeft, newResult, respondTo)
      else {
        respondTo ! AggregatedResult(newResult)
        context stop self
      }

    case ReceiveTimeout =>
      context stop self
  }
}