package quizzly.author

import akka.actor.typed.ActorRef
import akka.http.scaladsl.server.{Route, RequestContext}
import akka.http.scaladsl.server.Directives.*

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.*

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
  implicit val personFormat: RootJsonFormat[Person] = jsonFormat2(Person.apply)
  implicit val createQuizFormat: RootJsonFormat[CreateQuiz] = jsonFormat6(CreateQuiz.apply)
  implicit val quizListedFormat: RootJsonFormat[QuizListed] = jsonFormat6(QuizListed.apply)
  implicit val createSectionFormat: RootJsonFormat[CreateSection] = jsonFormat1(CreateSection.apply)
  implicit val updateQuizFormat: RootJsonFormat[UpdateQuiz] = jsonFormat3(UpdateQuiz.apply)
  implicit val updateSectionFormat: RootJsonFormat[UpdateSection] = jsonFormat2(UpdateSection.apply)

object HttpFrontend extends JsonFormats:
  
  // format: off
  def apply(read: Read) =
    extractRequestContext { request =>
      auth(request) { person => 
        pathPrefix("v1") {
          pathPrefix("quiz") {
            pathEnd {
              get {
                complete(read.getList())
              }
            }~
            post {
              entity(as[CreateQuiz]) { cq => 
                performQuiz(
                  cq.id,
                  Quiz.Create(
                    cq.id,
                    cq.title,
                    cq.intro,
                    person,
                    getPersons(cq.authors),
                    getPersons(cq.inspectors),
                    cq.recommendedLength,
                    _
                  )
                )
              }
            }~
            path(Segment) { quizID =>
              pathEnd {
                post {
                  entity(as[CreateSection]) { cs =>
                    performQuiz(
                      quizID,
                      Quiz.AddSection(cs.title, person, _)
                    )
                  }
                }~
                put {
                  entity(as[UpdateQuiz]) { uq =>
                    performQuiz(
                      quizID,
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
                    performQuiz(quizID, Quiz.OwnSection(sc, person, _))
                  }
                }~
                get {
                  complete("not implemented yet")
                }
              }~
              path("ready") {
                delete {
                  performQuiz(quizID, Quiz.UnsetReadySign(person, _))
                }~
                head {
                  performQuiz(quizID, Quiz.SetReadySign(person, _))
                }
              }~
              path("resolve") {
                delete {
                  performQuiz(quizID, Quiz.Resolve(person, false, _))
                }~
                head {
                  performQuiz(quizID, Quiz.Resolve(person, true, _))
                }
              }
            }
          }~
          path("section" / Segment) { sc =>
            pathEnd {
              put {
                entity(as[UpdateSection]) { us =>
                  performSection(sc, SectionEdit.Update(person, us.title, us.intro, _))
                }
              }~
              patch {
                parameter("up".as[Boolean], "qid") { (up, quizID) => 
                  performQuiz(quizID, Quiz.MoveSection(sc, up, person, _))
                }
              }~
              delete {
                parameter("qid") { quizID =>
                  performQuiz(quizID, Quiz.RemoveSection(sc, person, _))
                }
              }~
              head {
                performSection(sc, SectionEdit.Discharge(person, _))
              }
            }~
            path("items") {
              perform()
            }~
            path("items" / Segment) { itemID =>
              perform()
            }
          }
        }
      }
    }

  // format: on

  private def auth(context: RequestContext)(next: Person => Route) = ???
  private def getPersons(ids: Set[PersonID]): Set[Person] = ???

  private def perform(): Route = ???

  private def performQuiz(id: String, cmd: ActorRef[Resp[_]] => Quiz.Command): Route = ???
  private def performSection(sc: String, cmd: ActorRef[Resp[_]] => SectionEdit.Command): Route = ???
