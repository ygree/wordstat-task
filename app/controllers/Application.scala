package controllers

import play.api.mvc.{Action, Controller}

object Application extends Controller {
  def index = Action {
    Ok(views.html.index("Hello Play Framework"))
  }

  def search = Action { request =>
    val queries = request.queryString.get("query") getOrElse Nil

    if (queries.isEmpty) BadRequest("One or many 'query' parameters have to be provided.")
    else Ok(s"Yeahhhh! got: $queries")
  }
}