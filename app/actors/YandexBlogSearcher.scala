package actors

import scala.concurrent.Future
import play.api.libs.ws.{Response, WS}
import java.net.URI
import akka.pattern.PipeToSupport
import scala.util.Try
import scala.xml.NodeSeq
import akka.actor.{Props, ActorLogging, ActorRef}

object YandexBlogSearcher {
  case class Search(keyword: String)
  case class Found(links: Seq[URI])

  def apply(maxParallelConnections: Int = 2, numberOfDocuments: Int = 10) = Props(
    new YandexBlogSearcher(
      maxParallelConnections = maxParallelConnections,
      numberOfDocuments = numberOfDocuments
    )
  )
}

class YandexBlogSearcher(val maxParallelConnections: Int, numberOfDocuments: Int)
  extends BoundedParallelRequestProcessor[YandexBlogSearcher.Search]
  with PipeToSupport
  with ActorLogging
{
  import context.dispatcher
  import YandexBlogSearcher._

  def doRequest(request: Search, originalSender: ActorRef): Future[_] = {
    val url = linksQueryUrl(encodeUrl(request.keyword))
    val future = WS.url(url).get() map extractLinks map Found.apply
    future pipeTo originalSender
  }

  def linksQueryUrl(keyword: String) = s"http://blogs.yandex.ru/search.rss?text=$keyword&numdoc=$numberOfDocuments"

  def encodeUrl(url: String): String = {
    java.net.URLEncoder.encode(url, "UTF-8")
  }

  def extractLinks(response: Response): Seq[URI] = {
    val linksTags: NodeSeq = Try(response.xml) map { xml =>
      xml \\ "rss" \ "channel" \\ "item" \ "link"
    } getOrElse {
      log.warning(s"Incorrect response has been received: $response")
      NodeSeq.Empty
    }
    linksTags map (_.text) map URI.create
  }
}