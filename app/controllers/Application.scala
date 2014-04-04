package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.json._
import java.net.URI
import play.api.libs.concurrent.Akka
import actors.{BlogSearchResponseAggregator, BlogSearcher}
import akka.pattern.ask
import scala.concurrent.duration.Duration
import scala.concurrent.Future

object Application extends Controller {
  import scala.concurrent.ExecutionContext.Implicits.global

  import play.api.Play.current

  val blogSearcher = Akka.system.actorOf(
    BlogSearcher(maxParallelConnections = 2, numberOfDocuments = 10),
    "blogsearcher"
  )

  val blogSearcherTimeout = Duration(10, "sec")

  def search = Action.async { request =>
    val keywords = (request.queryString.get("query") getOrElse Nil).toSet
    if (keywords.nonEmpty) searchLinksByKeywords(keywords)
    else Future successful BadRequest("Please, provide at least one 'query' parameter.")
  }

  def searchLinksByKeywords(keywords: Set[String]) = {
    val aggregator = Akka.system.actorOf(BlogSearchResponseAggregator(blogSearcher, blogSearcherTimeout), "aggregator-"+System.nanoTime())
    import BlogSearchResponseAggregator._

    (aggregator ? BlogSearchResponseAggregator.Request(keywords))(blogSearcherTimeout).mapTo[AggregatedResult] map {
      case AggregatedResult(links) =>
        val json = Json.toJson(prepareSearchResponse(links))
        Ok(Json.prettyPrint(json))
    } fallbackTo {
      Future successful BadGateway(
        "Remote service wasn't able to proceed request before timeout or remote service has failed."
      )
    }
  }

  private def prepareSearchResponse(links: Set[URI]): Map[String, Int] = {
    def secondLevelDomainNameByHostName(hostname: String) =
      hostname.split('.').reverse.toList match {
        case t :: s :: _ => s"$s.$t"
        case otherwise   => otherwise mkString "."
      }

    def extractSecondLevelDomainName(uri: URI) = secondLevelDomainNameByHostName(uri.getHost)

    val sldNames = links.toSeq map extractSecondLevelDomainName
    sldNames groupBy identity map { case (k, v) => k -> v.size }
  }
}