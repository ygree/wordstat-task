package actors

import scala.concurrent.Future
import play.api.libs.ws.WS
import java.net.URI
import akka.pattern.PipeToSupport
import scala.util.{Success, Try}
import scala.xml.NodeSeq
import akka.actor.ActorRef

object YandexBlogSearcher {
  case class Search(keyword: String)
  case class Found(links: Seq[URI])
}

class YandexBlogSearcher extends BoundedParallelRequestProcessor[YandexBlogSearcher.Search] with PipeToSupport {

  import context.dispatcher
  import YandexBlogSearcher._

  val maxParallelConnections = 10
  val numberOfDocuments = 10

  def linksQueryUrl(keyword: String) = s"http://blogs.yandex.ru/search.rss?text=$keyword&numdoc=$numberOfDocuments"

  def doRequest(request: Search, originalSender: ActorRef): Future[_] = {
    val url = linksQueryUrl(encodeUrl(request.keyword))

    val future = WS.url(url).get() map { response =>
      val linksTags: NodeSeq = Try(response.xml) map { xml =>
        xml \\ "rss" \ "channel" \\ "item" \ "link"
      } getOrElse {
        NodeSeq.Empty
      }
//      val linksTags = response.xml \\ "rss" \ "channel" \\ "item" \ "link"
      val links = linksTags map (_.text) map URI.create
      Found(links)
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


  def encodeUrl(url: String): String = {
    java.net.URLEncoder.encode(url, "UTF-8")
  }
}