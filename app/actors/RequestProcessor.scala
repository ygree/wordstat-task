package actors

import scala.concurrent.Future
import play.api.libs.ws.WS
import java.net.URI
import akka.pattern.PipeToSupport
import scala.util.Try
import scala.xml.{NodeSeq, Node, Elem}

object RequestProcessor {
  case class Get(keyword: String)
  case class Response(links: Seq[URI])
}

class RequestProcessor extends BoundedParallelRequestProcessor[RequestProcessor.Get] with PipeToSupport {

  import context.dispatcher
  import RequestProcessor._

  val maxParallelConnections: Int = 1
  val numberOfDocuments = 10

  def linksQueryUrl(keyword: String) = s"http://blogs.yandex.ru/search.rss?text=$keyword&numdoc=$numberOfDocuments"

  def doRequest(request: Get): Future[_] = {
    val url = linksQueryUrl(request.keyword)
    WS.url(url).get() map { response =>
      println("xml: "+response.body)
      val linksTags = Try(response.xml) map { xml =>
        xml \\ "rss" \ "channel" \\ "item" \ "link"
      } getOrElse NodeSeq.Empty
      val links = linksTags map (_.text) map URI.create
      Response(links)
    } pipeTo sender
  }
}