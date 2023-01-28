package quizzly.trial

import akka.http.scaladsl.model.*

import scala.concurrent.Future
import scala.jdk.OptionConverters.*

object FakeAuth extends Auth:
  import scala.concurrent.ExecutionContext.Implicits.global
  
  val all = (1 to 10).map(n => (s"pers$n", Person(s"pers$n", s"pers$n name"))).toMap
  def authenticate(req: HttpRequest) =
    req.getHeader("p").toScala.map(_.value).flatMap(all.get(_)) match
      case Some(p: Person) =>
        Future(p)
      case _ =>
        Future.failed(Exception())
