package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.ws.WS
import play.api.libs.json._
import java.net.{URL, URI}

object Application extends Controller {
  import scala.concurrent.ExecutionContext.Implicits.global

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
      case otherwise   => otherwise.mkString(".")
    }

  def extractSecondLevelDomainName(uri: String): String = {
    val hostname = URI.create(uri).getHost
    secondLevelDomainNameByHostName(hostname)
  }

  def search =
    Action.async { request =>
      val f = WS.url("http://blogs.yandex.ru/search.rss?text=scala&numdoc=2").get() map {
        response =>
          val linksTags = response.xml \\ "rss" \ "channel" \\ "item" \ "link"
          val links = linksTags map (_.text) map extractSecondLevelDomainNamek

          val statistics = links groupBy identity map { case (k, v) => k -> v.size }
          val json = Json.toJson(statistics)
          val result = Json.prettyPrint(json)

          Ok("ok!\n"+result)
      }
      f
    }
}