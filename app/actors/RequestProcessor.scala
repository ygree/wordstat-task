package actors

import scala.concurrent.Future
import play.api.libs.ws.WS
import java.net.URI
import akka.pattern.PipeToSupport
import scala.util.{Success, Try}
import scala.xml.NodeSeq
import akka.actor.ActorRef

object RequestProcessor {
  case class Get(keyword: String)
  case class Response(links: Seq[URI])
}

class RequestProcessor extends BoundedParallelRequestProcessor[RequestProcessor.Get] with PipeToSupport {

  import context.dispatcher
  import RequestProcessor._

  val maxParallelConnections: Int = 1
  val numberOfDocuments = 1

  def linksQueryUrl(keyword: String) = s"http://blogs.yandex.ru/search.rss?text=$keyword&numdoc=$numberOfDocuments"

  def doRequest(request: Get, originalSender: ActorRef): Future[_] = {
    val url = linksQueryUrl(request.keyword)
    val future = WS.url(url).get() map { response =>
      val linksTags = Try(response.xml) map { xml =>
        xml \\ "rss" \ "channel" \\ "item" \ "link"
      } getOrElse NodeSeq.Empty
      val links = linksTags map (_.text) map URI.create
      Response(links)
    }
    future pipeTo originalSender
//    future onComplete {
//      case Success(r) =>
//        respondTo ! r
//        println(">>>>>>>>>>>>success")
//        println(">>>>>>>>>>>>respondTo: "+respondTo)
//      case otherwise  =>
//        respondTo ! otherwise
//        println(">>>>>>>>>>>>otherwise: "+otherwise)
//    }
//    future
  }
}