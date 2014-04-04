package actors

import akka.actor._
import java.net.URI
import scala.concurrent.duration.Duration

object BlogSearchResponseAggregator {

  case class Request(keywords: Set[String])
  case class AggregatedResult(uris: Set[URI])

  def apply(requestProcessor: ActorRef, timeout: Duration) = Props(
    new BlogSearchResponseAggregator(requestProcessor, timeout)
  )
}

class BlogSearchResponseAggregator(requestProcessor: ActorRef, timeout: Duration) extends Actor with ActorLogging {

  import BlogSearchResponseAggregator._

  def receive = {
    case Request(keywords) =>
      keywords foreach { keyword =>
        requestProcessor ! BlogSearcher.Search(keyword)
      }
      context become awaitResults(keywords.size, Set(), sender)
      context.setReceiveTimeout(timeout)
  }

  def awaitResults(left: Int, result: Set[URI] = Set(), respondTo: ActorRef): Receive = {
    case BlogSearcher.Found(links) =>
      val newLeft = left - 1
      val newResult = result ++ links
      if (newLeft > 0) context become awaitResults(newLeft, newResult, respondTo)
      else {
        respondTo ! AggregatedResult(newResult)
        log.info("All responses have come, stop itself")
        context stop self
      }

    case ReceiveTimeout =>
      log.info("Timeout, stop itself")
      context stop self
  }
}