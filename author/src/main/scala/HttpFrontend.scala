package quizzly.author

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.{Route, RequestContext}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.model.*

import akka.cluster.sharding.typed.scaladsl.EntityRef

import akka.stream.scaladsl.Source

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

trait JsonFormats extends SprayJsonSupport, DefaultJsonProtocol:
  given RootJsonFormat[Person] = jsonFormat2(Person.apply)
  given RootJsonFormat[CreateQuiz] = jsonFormat6(CreateQuiz.apply)
  given RootJsonFormat[QuizListed] = jsonFormat6(QuizListed.apply)
  given RootJsonFormat[CreateSection] = jsonFormat1(CreateSection.apply)
  given RootJsonFormat[UpdateQuiz] = jsonFormat3(UpdateQuiz.apply)
  given RootJsonFormat[UpdateSection] = jsonFormat2(UpdateSection.apply)
  given RootJsonFormat[Reason] = jsonFormat2(Reason.apply)
  given RootJsonFormat[Error] = jsonFormat2(Error.apply)
  given RootJsonFormat[Quiz.CreateDetails] = jsonFormat2(Quiz.CreateDetails.apply)

  given JsonEntityStreamingSupport = EntityStreamingSupport.json()

trait EntityAware:
  def quiz(id: String): EntityRef[Quiz.Command]
  def section(id: String): EntityRef[SectionEdit.Command]

trait Auth:
  def authenticate(request: HttpRequest): Future[Person]
  def getPersons: Future[Set[Person]]
  def getPersons(ids: Set[PersonID]): Future[Set[Person]]

object HttpFrontend extends JsonFormats:

  
  // format: off
  def apply(read: Read, entities: EntityAware, authService: Auth) (using ActorSystem[_], ExecutionContext)=
    
    def auth(request: HttpRequest)(next: Person => Route) = 
      onComplete(authService.authenticate(request)) {
        case Success(p) => next(p)
        case Failure(ex) => complete(StatusCodes.Unauthorized)
      }

    def completeCall(fut: Future[Resp[_]]) =
      onComplete(fut) {
        case Success(Resp.OK) => complete(StatusCodes.NoContent)
        case Success(Resp.Good(r)) => r match
          case s: String => complete(s)
          case c: Quiz.CreateDetails => complete(c)
          case l: List[String] => complete(Source(l))
          case _ => complete(StatusCodes.BadRequest, s"cannot serialize $r")
        case Success(Resp.Bad(e)) => complete(StatusCodes.ExpectationFailed, e)
        case Failure(ex) => complete(StatusCodes.InternalServerError, ex.getMessage)
      }

    given akka.util.Timeout = 2.seconds
      
    def onQuiz(id: String)(cmd: ActorRef[Resp[_]] => Quiz.Command) = 
      completeCall(entities.quiz(id).ask[Resp[_]](cmd))

    def onSection(id: String)(cmd: ActorRef[Resp[_]] => SectionEdit.Command) = 
      completeCall(entities.section(id).ask[Resp[_]](cmd))

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
            path(Segment) { quizID =>
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
                head {
                  parameter("sc") { sc =>
                    onQuiz(quizID)(Quiz.OwnSection(sc, person, _))
                  }
                }~
                get {
                  complete("not implemented yet")
                }
              }~
              path("ready") {
                delete {
                  onQuiz(quizID)(Quiz.UnsetReadySign(person, _))
                }~
                head {
                  onQuiz(quizID)(Quiz.SetReadySign(person, _))
                }
              }~
              path("resolve") {
                delete {
                  onQuiz(quizID)(Quiz.Resolve(person, false, _))
                }~
                head {
                  onQuiz(quizID)(Quiz.Resolve(person, true, _))
                }
              }
            }
          }~
          path("section" / Segment) { sc =>
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
              head {
                onSection(sc)(SectionEdit.Discharge(person, _))
              }
            }
            /*
            path("items") {
              perform()
            }~
            path("items" / Segment) { itemID =>
              perform()
            }
            */
          }
        }
      }
    }

  // format: on

