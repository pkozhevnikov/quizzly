package quizzly.author

import scala.concurrent.Future
import akka.http.scaladsl.model.*

object FakeAuth extends Auth:
  import scala.concurrent.ExecutionContext.Implicits.global
  val curator = Person("cur", "curator name")
  val author1 = Person("author1", "author1 name")
  val author2 = Person("author2", "author2 name")
  val author3 = Person("author3", "author3 name")
  val authors = Set(author1, author2)
  val inspector1 = Person("inspector1", "inspector1 name")
  val inspector2 = Person("inspector2", "inspector2 name")
  val inspector3 = Person("inspector3", "inspector3 name")
  val inspectors = Set(inspector1, inspector2)
  val all = Map(
    curator.id -> curator,
    author1.id -> author1,
    author2.id -> author2,
    author3.id -> author3,
    inspector1.id -> inspector1,
    inspector2.id -> inspector2,
    inspector3.id -> inspector3
  )
  def authenticate(request: HttpRequest) = Future(all(request.getHeader("p").get.value))
  def getPersons = Future(all.values.toSet)
  def getPersons(ids: Set[PersonID]) = Future(all.filter((k, v) => ids(k)).values.toSet)
  def getPerson(id: PersonID) = Future(all.get(id))
