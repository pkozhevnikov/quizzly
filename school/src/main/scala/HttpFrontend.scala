package quizzly.school

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.common.JsonEntityStreamingSupport
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.marshalling.ToEntityMarshaller
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

case class QuizRef(id: String, title: String)

case class ExamView(
    id: String,
    quiz: QuizRef,
    period: ExamPeriod,
    host: Official,
    state: String,
    cancelledAt: Option[Instant],
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

case class ChangeLength(length: Int)

trait JsonFormats extends SprayJsonSupport, DefaultJsonProtocol:
  given RootJsonFormat[Reason] = jsonFormat2(Reason.apply)
  given RootJsonFormat[Error] = jsonFormat2(Error.apply)
  given RootJsonFormat[ChangeLength] = jsonFormat1(ChangeLength.apply)
  given RootJsonFormat[Instant] =
    new:
      def write(i: Instant) = JsString(i.toString)
      def read(n: JsValue) =
        n match
          case JsString(str) =>
            Instant.parse(str)
          case x =>
            throw DeserializationException(s"unknown value for intant $x")
  given RootJsonFormat[ZonedDateTime] =
    new:
      def write(zdt: ZonedDateTime) = JsString(zdt.toString)
      def read(n: JsValue) =
        n match
          case JsString(str) =>
            ZonedDateTime.parse(str)
          case x =>
            throw DeserializationException(s"unknown value for intant $x")
  given RootJsonFormat[Person] =
    new:
      def write(p: Person) = JsObject(
        "id" -> JsString(p.id),
        "name" -> JsString(p.name),
        "place" -> JsString(p.place)
      )
      def read(n: JsValue) =
        n.asJsObject.getFields("place", "id", "name") match
          case Seq(JsString(place), JsString(id), JsString(name)) =>
            Person.of(place)(id, name)
          case x =>
            throw DeserializationException(s"cannot deserialize person $x")
  given RootJsonFormat[ExamPeriod] = jsonFormat2(ExamPeriod.apply)
  given RootJsonFormat[Official] = jsonFormat(Official.apply, "id", "name")
  given RootJsonFormat[Student] = jsonFormat(Student.apply, "id", "name")
  given RootJsonFormat[QuizRef] = jsonFormat2(QuizRef.apply)
  given RootJsonFormat[CreateExam] = jsonFormat6(CreateExam.apply)
  given RootJsonFormat[Exam.CreateExamDetails] = jsonFormat2(Exam.CreateExamDetails.apply)
  given RootJsonFormat[ExamView] = jsonFormat8(ExamView.apply)
  given RootJsonFormat[QuizListed] = jsonFormat6(QuizListed.apply)
  given RootJsonFormat[Set[Person]] =
    new:
      def write(s: Set[Person]) = JsArray(s.map(_.toJson).toList)
      def read(n: JsValue) = n.convertTo[Set[Person]]

  given JsonEntityStreamingSupport = EntityStreamingSupport.json()

trait EntityAware:
  def exam(id: String): EntityRef[Exam.Command]

trait Auth:
  def authenticate(request: HttpRequest): Future[Official]
  def getPersons: Future[Set[Person]]
  def getPersons(ids: Set[PersonID]): Future[Set[Person]]
  def getPerson(id: PersonID): Future[Option[Person]]

trait Read:
  def examList()(using ExecutionContext): Future[List[ExamView]]
  def testees(examId: ExamID)(using ExecutionContext): Future[List[Person]]
  def quizList()(using ExecutionContext): Future[List[QuizListed]]

object HttpFrontend extends JsonFormats:

  val log = org.slf4j.LoggerFactory.getLogger("HttpFrontend")

  def apply(
      read: Read,
      entities: EntityAware,
      authService: Auth,
      host: String = "localhost",
      port: Int = 9099
  )(using sys: ActorSystem[?], ec: ExecutionContext, now: () => Instant) =

    def auth(request: HttpRequest)(next: Official => Route) =
      onComplete(authService.authenticate(request)) {
        case Success(p) =>
          next(p)
        case Failure(ex) =>
          complete(StatusCodes.Unauthorized)
      }

    given akka.util.Timeout = 2.seconds

    def onExam[R](id: String)(cmd: ActorRef[Resp[R]] => Exam.Command)(using ToEntityMarshaller[R]) =
      onComplete(entities.exam(id).ask(cmd)) {
        case Success(Resp.OK) =>
          complete(StatusCodes.NoContent)
        case Success(Resp.Good(r)) =>
          complete(r)
        case Success(Resp.Bad(e)) =>
          complete(StatusCodes.UnprocessableEntity, e)
        case Failure(ex) =>
          complete(StatusCodes.InternalServerError, ex.getMessage)
      }

    // format: off
    pathPrefix("pubapi")(pubapi(host, port)) ~
    extractRequest { request =>
      auth(request) { person =>
        pathPrefix("v1") {
          path("persons") {
            onSuccess(authService.getPersons.map(Source(_)))(complete)
          } ~
          path("quiz") {
            get {
              onComplete(read.quizList()) {
                case Success(r) =>
                  complete(Source(r))
                case Failure(ex) =>
                  complete(StatusCodes.InternalServerError, ex.getMessage)
              }
            }
          } ~
          pathPrefix("exam") {
            pathEnd {
              get {
                onComplete(read.examList()) {
                  case Success(r) =>
                    complete(Source(r))
                  case Failure(ex) =>
                    complete(StatusCodes.InternalServerError, ex.getMessage)
                }
              } ~
              post {
                entity(as[CreateExam]) { ce =>
                  onComplete(authService.getPersons(ce.testees)) {
                    case Success(set) =>
                      onExam[Exam.CreateExamDetails](ce.id)(
                        Exam.Create(
                          ce.quizId,
                          ce.trialLength,
                          ExamPeriod(ce.start, ce.end),
                          set,
                          person,
                          _
                        )
                      )
                    case Failure(ex) =>
                      complete(StatusCodes.InternalServerError, ex.getMessage)
                  }
                }
              }
            } ~
            path(Segment) { id =>
              get {
                onComplete(read.testees(id)) {
                  case Success(r) =>
                    complete(Source(r))
                  case Failure(ex) =>
                    complete(StatusCodes.InternalServerError, ex.getMessage)
                }
              } ~
              post {
                entity(as[ChangeLength]) { cl =>
                  onExam[Nothing](id)(Exam.SetTrialLength(cl.length, _))
                }
              } ~
              put {
                entity(as[Set[String]]) { inc =>
                  onComplete(authService.getPersons(inc)) {
                    case Success(set) =>
                      onExam[Set[Person]](id)(Exam.IncludeTestees(set, _))
                    case Failure(ex) =>
                      complete(StatusCodes.InternalServerError, ex.getMessage)
                  }
                }
              } ~
              patch {
                entity(as[Set[String]]) { exc =>
                  onComplete(authService.getPersons(exc)) {
                    case Success(set) =>
                      onExam[Set[Person]](id)(Exam.ExcludeTestees(set, _))
                    case Failure(ex) =>
                      complete(StatusCodes.InternalServerError, ex.getMessage)
                  }
                }
              } ~
              delete {
                onExam[Nothing](id)(Exam.Cancel(now(), _))
              }
            }
          }
        }
      }
    }
  // format: on

  val yamlContentType = ContentType(
    MediaType.textWithFixedCharset("vnd.yaml", HttpCharsets.`UTF-8`, "yaml", "yml")
  )
  private def pubapi(host: String, port: Int) = concat(
    pathSingleSlash(getFromResource("pubapi/index.html")),
    path("root.yaml") {
      complete(
        HttpEntity(
          yamlContentType,
          Source
            .fromIterator(() => scala.io.Source.fromResource("pubapi/root.yaml").getLines())
            .map(_.replace("""${http.host}""", host).replace("""${http.port}""", port.toString))
            .map(l => ByteString(s"$l\n"))
        )
      )
    },
    getFromResourceDirectory("pubapi")
  )
