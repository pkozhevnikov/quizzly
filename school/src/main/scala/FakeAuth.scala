package quizzly.school

import akka.http.scaladsl.model.*

import scala.concurrent.Future
import scala.jdk.OptionConverters.*

object FakeAuth extends Auth:
  import scala.concurrent.ExecutionContext.Implicits.global
  val off1 = Official("off1", "off1 name")
  val off2 = Official("off2", "off2 name")
  val off3 = Official("off3", "off3 name")
  val off4 = Official("off4", "off4 name")

  val stud1 = Student("stud1", "stud1 name")
  val stud2 = Student("stud2", "stud2 name")
  val stud3 = Student("stud3", "stud3 name")
  val stud4 = Student("stud4", "stud4 name")
  val stud5 = Student("stud5", "stud5 name")
  val stud6 = Student("stud6", "stud6 name")
  val stud7 = Student("stud7", "stud7 name")
  val stud8 = Student("stud8", "stud8 name")

  val all = Map(
    off1.id -> off1,
    off2.id -> off2,
    off3.id -> off3,
    off4.id -> off4,
    stud1.id -> stud1,
    stud2.id -> stud2,
    stud3.id -> stud3,
    stud4.id -> stud4,
    stud5.id -> stud5,
    stud6.id -> stud6,
    stud7.id -> stud7,
    stud8.id -> stud8
  )
  def authenticate(req: HttpRequest) =
    req.getHeader("p").toScala.map(_.value).flatMap(all.get(_)) match
      case Some(p: Official) =>
        Future(p)
      case _ =>
        Future.failed(Exception())
  def getPersons = Future(all.values.toSet)
  def getPersons(ids: Set[PersonID]) = Future(all.filter((k, v) => ids(k)).values.toSet)
  def getPerson(id: PersonID) = Future(all.get(id))
