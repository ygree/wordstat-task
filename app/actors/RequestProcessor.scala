package actors

import scala.concurrent.Future
import play.api.libs.ws.WS
import java.net.URI
import akka.pattern.PipeToSupport

object RequestProcessor {
  case class Get(keyword: String)
  case class Response(links: Seq[URI])
}

class RequestProcessor extends BoundedParallelRequestProcessor[RequestProcessor.Get] with PipeToSupport {

  import context.dispatcher
  import RequestProcessor._

  val maxParallelConnections: Int = 1
  val numberOfDocuments = 2

  def linksQueryUrl(keyword: String) = s"http://blogs.yandex.ru/search.rss?text=$keyword&numdoc=$numberOfDocuments"

  def doRequest(request: Get): Future[_] = {
    val url = linksQueryUrl(request.keyword)
    WS.url(url).get() map { response =>
      val linksTags = response.xml \\ "rss" \ "channel" \\ "item" \ "link"
      val links = linksTags map (_.text) map URI.create
      Response(links)
    } pipeTo sender
  }
}