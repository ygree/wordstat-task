package actors

import akka.actor.{ActorRef, Actor}
import java.net.URI

object ResponseAggregator {

  case class Request(keywords: Set[String])
  case class AggregatedResult(uris: Set[URI])
}

class ResponseAggregator(requestProcessor: ActorRef) extends Actor {

  import ResponseAggregator._

  def receive = {
    case Request(keywords) =>
      keywords foreach { keyword =>
        requestProcessor ! RequestProcessor.Get(keyword)
      }
      context become awaitResults(keywords.size, Set(), sender)

      //TODO timeout!
  }

  def awaitResults(left: Int, result: Set[URI] = Set(), respondTo: ActorRef): Receive = {
    case RequestProcessor.Response(links) =>
      val newLeft = left - 1
      val newResult = result ++ links
      println(s">>>>>>>>>got response. left: $newLeft")
      if (newLeft > 0) context become awaitResults(newLeft, newResult, respondTo)
      else {
//        println(s"got response: $result. left: $newLeft")
        respondTo ! AggregatedResult(newResult)
//        context stop self
      }
    case x => println(s">>>>>>>>>$x")
  }
}