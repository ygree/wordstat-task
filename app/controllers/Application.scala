package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.ws.WS
import play.api.libs.json._
import java.net.URI
import play.api.libs.concurrent.Akka
import akka.actor.Props
import actors.{ResponseAggregator, YandexBlogSearcher}
import akka.pattern.ask
import scala.concurrent.duration.Duration

object Application extends Controller {
  import scala.concurrent.ExecutionContext.Implicits.global

  import play.api.Play.current

  val blogSearcher = Akka.system.actorOf(Props[YandexBlogSearcher], "blogsearcher")

  def index = Action {
    Ok(views.html.index("Hello Play Framework"))
  }

  def search0 = Action { request =>
    val queries = request.queryString.get("query") getOrElse Nil

    if (queries.isEmpty) BadRequest("One or many 'query' parameters have to be provided.")
    else Ok(s"Yeahhhh! got: $queries")
  }

  def secondLevelDomainNameByHostName(hostname: String): String =
    hostname.split('.').reverse.toList match {
      case t :: s :: _ => s"$s.$t"
      case otherwise   => otherwise mkString "."
    }

  def extractSecondLevelDomainName(uri: URI): String = secondLevelDomainNameByHostName(uri.getHost)

  def search1 =
    Action.async { request =>
      val f = WS.url("http://blogs.yandex.ru/search.rss?text=scala&numdoc=2").get() map {
        response =>
          val linksTags = response.xml \\ "rss" \ "channel" \\ "item" \ "link"
          val links = linksTags map (_.text) map URI.create

          val sldNames = links map extractSecondLevelDomainName
          val statistics = sldNames groupBy identity map { case (k, v) => k -> v.size }
          val json = Json.toJson(statistics)
          val result = Json.prettyPrint(json)

          Ok("ok!\n"+result)
      }
      f
    }

  def search2 = Action.async { request =>
    import YandexBlogSearcher._
    //TODO get keywords from request
    (blogSearcher ? Search("scala"))(Duration(5, "sec")).mapTo[Found] map { case Found(links) =>

      val sldNames = links map extractSecondLevelDomainName
      val statistics = sldNames groupBy identity map { case (k, v) => k -> v.size }
      val json = Json.toJson(statistics)
      val result = Json.prettyPrint(json)

      Ok(result)
    }
  }

  def search = Action.async { request =>
    import ResponseAggregator._
    val keywords = (request.queryString.get("query") getOrElse Nil).toSet
    val aggregator = Akka.system.actorOf(Props(new ResponseAggregator(blogSearcher)))

    (aggregator ? ResponseAggregator.Request(keywords))(Duration(60, "sec")).mapTo[AggregatedResult] map {
      case AggregatedResult(links) =>

        val sldNames = links.toSeq map extractSecondLevelDomainName
        val statistics = sldNames groupBy identity map { case (k, v) => k -> v.size }
        val json = Json.toJson(statistics)
        val result = Json.prettyPrint(json)

        Ok(result)
    }
  }
}