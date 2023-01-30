package quizzly.trial

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

import ch.megard.akka.http.cors.scaladsl.CorsDirectives.*

case class ExamListed(
    id: ExamID,
    quizId: QuizID,
    quizTitle: String,
    start: Instant,
    end: Instant,
    trialLength: Int
)

case class ExamInfo(
    quizId: QuizID,
    quizTitle: String,
    quizIntro: String,
    id: ExamID,
    start: Instant,
    end: Instant,
    trialLength: Int
)
case class Solution(itemSC: SC, answers: List[String])

trait JsonFormats extends SprayJsonSupport, DefaultJsonProtocol:
  given RootJsonFormat[StartTrialDetails] = jsonFormat5(StartTrialDetails.apply)
  given RootJsonFormat[ExamListed] = jsonFormat6(ExamListed.apply)
  given RootJsonFormat[ExamInfo] = jsonFormat7(ExamInfo.apply)
  given RootJsonFormat[Reason] = jsonFormat2(Reason.apply)
  given RootJsonFormat[Error] = jsonFormat2(Error.apply)
  given RootJsonFormat[Person] = jsonFormat2(Person.apply)
  given RootJsonFormat[Instant] =
    new:
      def write(i: Instant) = JsString(i.toString)
      def read(n: JsValue) =
        n match
          case JsString(str) =>
            Instant.parse(str)
          case x =>
            throw DeserializationException(s"unknown value for instant $x")
  given RootJsonFormat[ZonedDateTime] =
    new:
      def write(zdt: ZonedDateTime) = JsString(zdt.toString)
      def read(n: JsValue) =
        n match
          case JsString(str) =>
            ZonedDateTime.parse(str)
          case x =>
            throw DeserializationException(s"unknown value for zoned datetime $x")
  given RootJsonFormat[Statement] = jsonFormat2(Statement.apply)
  given RootJsonFormat[ItemView] = jsonFormat5(ItemView.apply)
  given RootJsonFormat[SectionView] = jsonFormat3(SectionView.apply)
  given RootJsonFormat[Solution] = jsonFormat2(Solution.apply)
  given RootJsonFormat[SubmissionResult] = jsonFormat2(SubmissionResult.apply)

  given JsonEntityStreamingSupport = EntityStreamingSupport.json()

trait EntityAware:
  def exam(id: ExamID): EntityRef[ExamEntity.Command]
  def trial(id: TrialID): EntityRef[Trial.Command]

trait Auth:
  def authenticate(request: HttpRequest): Future[Person]

trait Read:
  def examList()(using ExecutionContext): Future[List[ExamListed]]
  def examInfo(id: ExamID)(using ExecutionContext): Future[ExamInfo]

object HttpFrontend extends JsonFormats:

  val log = org.slf4j.LoggerFactory.getLogger("HttpFrontend")

  def apply(
      quizRegistry: QuizRegistry,
      entities: EntityAware,
      authService: Auth,
      host: String = "localhost",
      port: Int = 9099
  )(using sys: ActorSystem[?], ec: ExecutionContext, now: () => Instant) =

    def auth(request: HttpRequest)(next: Person => Route) =
      onComplete(authService.authenticate(request)) {
        case Success(p) =>
          next(p)
        case Failure(ex) =>
          complete(StatusCodes.Unauthorized)
      }

    given akka.util.Timeout = 2.seconds

    def call[R, Cmd](
        entity: EntityRef[Cmd]
    )(cmd: ActorRef[Resp[R]] => Cmd)(using ToEntityMarshaller[R]) =
      onComplete(entity.ask(cmd)) {
        case Success(Resp.OK) =>
          complete(StatusCodes.NoContent)
        case Success(Resp.Good(r)) =>
          complete(r)
        case Success(Resp.Bad(e)) =>
          complete(StatusCodes.UnprocessableEntity, e)
        case Failure(ex) =>
          log.error(ex.getMessage, ex)
          complete(StatusCodes.InternalServerError, ex.getMessage)
      }

    def onTrial[R](id: TrialID)(cmd: ActorRef[Resp[R]] => Trial.Command)(using
        ToEntityMarshaller[R]
    ) = call[R, Trial.Command](entities.trial(id))(cmd)

    def onExam[R](id: ExamID)(cmd: ActorRef[Resp[R]] => ExamEntity.Command)(using
        ToEntityMarshaller[R]
    ) = call[R, ExamEntity.Command](entities.exam(id))(cmd)

    // format: off
    cors() {
      pathPrefix("pubapi")(pubapi(host, port)) ~
      extractRequest { request =>
        auth(request) { person =>
          pathPrefix("v1") {
            path(Segment) { id =>
              get {
                onComplete[Resp[ExamInfo]](
                  entities.exam(id).ask[Resp[ExamEntity.ExamAttrs]](ExamEntity.GetInfo(_)).flatMap {
                    case Resp.Good(attrs) =>
                      quizRegistry.get(attrs.quiz).map { quiz =>
                        Resp.Good(ExamInfo(quiz.id, quiz.title, quiz.intro,
                          attrs.id, attrs.start, attrs.end, attrs.trialLength))
                      }
                    case Resp.Bad(e) =>
                      Future(Resp.Bad(e))
                  }
                ) {
                  case Success(Resp.Good(r)) =>
                    complete(r)
                  case Success(Resp.Bad(e)) =>
                    complete(StatusCodes.UnprocessableEntity, e)
                  case Failure(ex) =>
                    log.error(ex.getMessage, ex)
                    complete(StatusCodes.InternalServerError, ex.getMessage)
                }
              } ~
              patch {
                onTrial[StartTrialDetails](s"${person.id}-$id")(Trial.Start(person, id, _))
              } ~
              post {
                entity(as[Solution]) { solution =>
                  onTrial[SubmissionResult](id)(Trial.Submit(person, solution.itemSC, solution.answers, _))
                }
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
