package quizzly.author

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.{Route, RequestContext}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.model.*

import akka.cluster.sharding.typed.scaladsl.EntityRef

import akka.stream.scaladsl.Source
import akka.util.ByteString

import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.marshallers.sprayjson.{SprayJsonSupport}
import spray.json.*

import scala.util.{Success, Failure, Try}
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}

case class CreateQuiz(
    id: String,
    title: String,
    intro: String,
    recommendedLength: Int,
    authors: Set[String],
    inspectors: Set[String]
)
case class CreateSection(title: String)
case class UpdateQuiz(title: String, intro: String, recommendedLength: Int)
case class UpdateSection(title: String, intro: String)
case class StrList(list: List[String])

trait JsonFormats extends SprayJsonSupport, DefaultJsonProtocol:
  given RootJsonFormat[Person] = jsonFormat2(Person.apply)
  given RootJsonFormat[CreateQuiz] = jsonFormat6(CreateQuiz.apply)
  given RootJsonFormat[Quiz.State] =
    new:
      def write(s: Quiz.State) = JsString(s.toString)
      def read(n: JsValue) =
        n match
          case JsString("Composing") =>
            Quiz.State.Composing
          case JsString("Review") =>
            Quiz.State.Review
          case JsString("Released") =>
            Quiz.State.Released
          case x =>
            throw DeserializationException(s"unknown value $x")
  given RootJsonFormat[QuizListed] = jsonFormat7(QuizListed.apply)
  given RootJsonFormat[Statement] = jsonFormat2(Statement.apply)
  given RootJsonFormat[Item] = jsonFormat6(Item.apply)
  given RootJsonFormat[Section] = jsonFormat4(Section.apply)
  given RootJsonFormat[FullQuiz] = jsonFormat13(FullQuiz.apply)
  given RootJsonFormat[CreateSection] = jsonFormat1(CreateSection.apply)
  given RootJsonFormat[UpdateQuiz] = jsonFormat3(UpdateQuiz.apply)
  given RootJsonFormat[UpdateSection] = jsonFormat2(UpdateSection.apply)
  given RootJsonFormat[Reason] = jsonFormat2(Reason.apply)
  given RootJsonFormat[Error] = jsonFormat2(Error.apply)
  given RootJsonFormat[Quiz.CreateDetails] = jsonFormat2(Quiz.CreateDetails.apply)
  given RootJsonFormat[StrList] = jsonFormat1(StrList.apply)

  given JsonEntityStreamingSupport = EntityStreamingSupport.json()

trait EntityAware:
  def quiz(id: String): EntityRef[Quiz.Command]
  def section(id: String): EntityRef[SectionEdit.Command]

trait Auth:
  def authenticate(request: HttpRequest): Future[Person]
  def getPersons: Future[Set[Person]]
  def getPersons(ids: Set[PersonID]): Future[Set[Person]]
  def getPerson(id: PersonID): Future[Option[Person]]

object HttpFrontend extends JsonFormats:

  val log = org.slf4j.LoggerFactory.getLogger("HttpFrontend")

  def apply(
      read: Read,
      entities: EntityAware,
      authService: Auth,
      host: String = "localhost",
      port: Int = 9099
  )(using ActorSystem[_], ExecutionContext) =

    def auth(request: HttpRequest)(next: Person => Route) =
      onComplete(authService.authenticate(request)) {
        case Success(p) =>
          next(p)
        case Failure(ex) =>
          complete(StatusCodes.Unauthorized)
      }

    def completeCall(fut: Future[Resp[_]]) =
      onComplete(fut) {
        case Success(Resp.OK) =>
          complete(StatusCodes.NoContent)
        case Success(Resp.Good(r)) =>
          r match
            case s: String =>
              complete(s)
            case c: Quiz.CreateDetails =>
              complete(c)
            case l: List[?] =>
              complete(StrList(l.map(_.toString)))
            case f: FullQuiz =>
              complete(f)
            case _ =>
              complete(StatusCodes.BadRequest, s"cannot serialize $r")
        case Success(Resp.Bad(e)) =>
          complete(StatusCodes.UnprocessableEntity, e)
        case Failure(ex) =>
          complete(StatusCodes.InternalServerError, ex.getMessage)
      }

    given akka.util.Timeout = 2.seconds

    def onQuiz(id: String)(cmd: ActorRef[Resp[_]] => Quiz.Command) =
      val quizent = entities.quiz(id)
      completeCall(quizent.ask[Resp[_]](cmd))

    def onSection(id: String)(cmd: ActorRef[Resp[_]] => SectionEdit.Command) = completeCall(
      entities.section(id).ask[Resp[_]](cmd)
    )

    // format: off
    pathPrefix("pubapi")(pubapi(host, port))~
    extractRequest { request =>
      auth(request) { person => 
        pathPrefix("v1") {
          path("staff") {
            onSuccess(authService.getPersons.map(Source(_)))(complete)
          }~
          pathPrefix("quiz") {
            pathEnd {
              get {
                onComplete(read.getList()) {
                  case Success(r) => complete(Source(r))
                  case Failure(ex) => complete(StatusCodes.InternalServerError, ex.getMessage)
                }
              }
            }~
            post {
              entity(as[CreateQuiz]) { cq => 
                onSuccess(
                  authService.getPersons(cq.authors)
                    .zip(authService.getPersons(cq.inspectors))
                ) { (authors, inspectors) =>
                  onQuiz(cq.id)(
                    Quiz.Create(
                      cq.id,
                      cq.title,
                      cq.intro,
                      person,
                      authors,
                      inspectors,
                      cq.recommendedLength,
                      _
                    )
                  )
                }
              }
            }~
            pathPrefix(Segment) { quizID =>
              pathEnd {
                post {
                  entity(as[CreateSection]) { cs =>
                    onQuiz(quizID)(Quiz.AddSection(cs.title, person, _))
                  }
                }~
                put {
                  entity(as[UpdateQuiz]) { uq =>
                    onQuiz(quizID)(
                      Quiz.Update(
                        uq.title,
                        uq.intro,
                        uq.recommendedLength,
                        person,
                        _
                      )
                    )
                  }
                }~
                patch {
                  parameter("sc") { sc =>
                    onQuiz(quizID)(Quiz.OwnSection(sc, person, _))
                  }
                }~
                get {
                  onQuiz(quizID)(Quiz.Get(person, _))
                }~
                delete {
                  onQuiz(quizID)(Quiz.SetObsolete(person, _))
                }
              }~
              path("ready") {
                delete {
                  onQuiz(quizID)(Quiz.UnsetReadySign(person, _))
                }~
                patch {
                  onQuiz(quizID)(Quiz.SetReadySign(person, _))
                }
              }~
              path("resolve") {
                delete {
                  onQuiz(quizID)(Quiz.Resolve(person, false, _))
                }~
                patch {
                  onQuiz(quizID)(Quiz.Resolve(person, true, _))
                }
              }~
              path("authors" / Segment) { authorId =>
                patch {
                  onComplete(authService.getPerson(authorId)) {
                    case Success(o) => o match
                      case Some(author) =>
                        onQuiz(quizID)(Quiz.AddAuthor(person, author, _))
                      case None =>
                        complete(StatusCodes.UnprocessableEntity, Quiz.personNotFound.error() + authorId)
                    case Failure(ex) =>
                      log.error("could not retrieve person from auth", ex)
                      complete(StatusCodes.InternalServerError, Quiz.unprocessed(ex.getMessage))
                  }
                }~
                delete {
                  onComplete(authService.getPerson(authorId)) {
                    case Success(o) => o match
                      case Some(author) =>
                        onQuiz(quizID)(Quiz.RemoveAuthor(person, author, _))
                      case None =>
                        complete(StatusCodes.UnprocessableEntity, Quiz.personNotFound.error() + authorId)
                    case Failure(ex) =>
                      log.error("could not retrieve person from auth", ex)
                      complete(StatusCodes.InternalServerError, Quiz.unprocessed(ex.getMessage))
                  }
                }
              }~
              path("inspectors" / Segment) { inspectorId =>
                patch {
                  onComplete(authService.getPerson(inspectorId)) {
                    case Success(o) => o match
                      case Some(inspector) =>
                        onQuiz(quizID)(Quiz.AddInspector(person, inspector, _))
                      case None =>
                        complete(StatusCodes.UnprocessableEntity, Quiz.personNotFound.error() + inspectorId)
                    case Failure(ex) =>
                      log.error("could not retrieve person from auth", ex)
                      complete(StatusCodes.InternalServerError, Quiz.unprocessed(ex.getMessage))
                  }
                }~
                delete {
                  onComplete(authService.getPerson(inspectorId)) {
                    case Success(o) => o match
                      case Some(inspector) =>
                        onQuiz(quizID)(Quiz.RemoveInspector(person, inspector, _))
                      case None =>
                        complete(StatusCodes.UnprocessableEntity, Quiz.personNotFound.error() + inspectorId)
                    case Failure(ex) =>
                      log.error("could not retrieve person from auth", ex)
                      complete(StatusCodes.InternalServerError, Quiz.unprocessed(ex.getMessage))
                  }
                }
              }
            }
          }~
          pathPrefix("section" / Segment) { sc =>
            pathEnd {
              put {
                entity(as[UpdateSection]) { us =>
                  onSection(sc)(SectionEdit.Update(person, us.title, us.intro, _))
                }
              }~
              patch {
                parameter("up".as[Boolean], "qid") { (up, quizID) => 
                  onQuiz(quizID)(Quiz.MoveSection(sc, up, person, _))
                }
              }~
              delete {
                parameter("qid") { quizID =>
                  onQuiz(quizID)(Quiz.RemoveSection(sc, person, _))
                }
              }~
              get {
                onSection(sc)(SectionEdit.Discharge(person, _))
              }
            }~
            path("items") {
              patch {
                onSection(sc)(SectionEdit.AddItem(person, _))
              }~
              put { entity(as[Item]) { item =>
                onSection(sc)(SectionEdit.SaveItem(person, item, _))
              }}
            }~
            path("items" / Segment) { itemID =>
              delete {
                onSection(sc)(SectionEdit.RemoveItem(person, itemID, _))
              }~
              patch { 
                parameter("up".as[Boolean]) { up =>
                  onSection(sc)(SectionEdit.MoveItem(person, itemID, up, _))
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
