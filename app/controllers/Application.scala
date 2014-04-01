package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.ws.WS

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

  val LinkUrlPattern = """\:\/\/(.+?)[\/|$|\?]""".r

  def search =
    Action.async { request =>
      val f = WS.url("http://blogs.yandex.ru/search.rss?text=scala&numdoc=2").get() map {
        response =>
          val linksTags = response.xml \\ "rss" \ "channel" \\ "item" \ "link"
          val links = linksTags map (_.text) flatMap { uri =>
            LinkUrlPattern.findFirstMatchIn(uri) map (_.group(1))
            //TODO should also keep only two levels of domain name like "xxx.xx"
          }

          val result = links.mkString("\n")
          Ok("ok!\n"+result)
      }
      f
    }
}