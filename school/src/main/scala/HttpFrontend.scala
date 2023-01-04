package quizzly.school

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.common.JsonEntityStreamingSupport
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.*
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import akka.util.ByteString
import spray.json.*

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import java.time.*

case class CreateExam(
  id: String,
  quizId: String,
  trialLength: Int,
  start: ZonedDateTime,
  end: ZonedDateTime,
  testees: Set[String]
)

case class CreateExamDetails(
  prestartAt: ZonedDateTime,
  host: Official
)

case class QuizRef(
  id: String,
  title: String
)

case class ExamView(
  id: String,
  quiz: QuizRef,
  period: ExamPeriod,
  host: Official,
  state: String,
  cancelledAt: Instant,
  trialLength: Int,
  prestartAt: ZonedDateTime
)

case class QuizListed(
  id: String,
  title: String,
  obsolete: Boolean,
  inUse: Boolean,
  isPublished: Boolean,
  everPublished: Boolean
)

trait JsonFormats extends SprayJsonSupport, DefaultJsonProtocol:
  given RootJsonFormat[Instant] = new:
    def write(i: Instant) = JsString(i.toString)
    def read(n: JsValue) = n match
      case JsString(str) => Instant.parse(str)
      case x => throw DeserializationException(s"unknown value for intant $x")
  given RootJsonFormat[ZonedDateTime] = new:
    def write(zdt: ZonedDateTime) = JsString(zdt.toString)
    def read(n: JsValue) = n match
      case JsString(str) => ZonedDateTime.parse(str)
      case x => throw DeserializationException(s"unknown value for intant $x")
  given RootJsonFormat[Person] = new:
    def write(p: Person) = JsObject(
      "id" -> JsString(p.id),
      "name" -> JsString(p.name),
      "place" -> JsString(p.place)
    )
    def read(n: JsValue) = n.asJsObject.fields("place") match
      case JsString("Official") => n.convertTo[Official]
      case JsString("Student") => n.convertTo[Student]
      case x => throw DeserializationException(s"unknown place for person $x")
  given RootJsonFormat[ExamPeriod] = jsonFormat2(ExamPeriod.apply)
  given RootJsonFormat[Official] = jsonFormat2(Official.apply)
  given RootJsonFormat[Student] = jsonFormat2(Student.apply)
  given RootJsonFormat[QuizRef] = jsonFormat2(QuizRef.apply)
  given RootJsonFormat[CreateExam] = jsonFormat6(CreateExam.apply)
  given RootJsonFormat[CreateExamDetails] = jsonFormat2(CreateExamDetails.apply)
  given RootJsonFormat[ExamView] = jsonFormat8(ExamView.apply)
  given RootJsonFormat[QuizListed] = jsonFormat6(QuizListed.apply)

