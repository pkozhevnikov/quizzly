package quizzly.author

import akka.actor.typed.*
import scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.testkit.scaladsl.TestEntityRef

import akka.http.scaladsl.model.*
import akka.http.scaladsl.testkit.ScalatestRouteTest

import akka.actor.testkit.typed.scaladsl.ActorTestKit

import org.scalatest.*

import scala.concurrent.{ExecutionContext, Future}

class HttpFrontendSpec
    extends wordspec.AnyWordSpec,
      ScalatestRouteTest,
      JsonFormats,
      matchers.should.Matchers:

  val log = org.slf4j.LoggerFactory.getLogger("HttpFrontendSpec")

  val testKit = ActorTestKit("frontendkit")
  given ActorSystem[_] = testKit.system

  val p1 = Person("p1", "p1 name")
  val p2 = Person("p2", "p2 name")
  val p3 = Person("p3", "p3 name")
  val p4 = Person("p4", "p4 name")
  val p5 = Person("p5", "p5 name")

  val persons = Map("p1" -> p1, "p2" -> p2, "p3" -> p3, "p4" -> p4, "p5" -> p5)

  object auth extends Auth:
    def authenticate(req: HttpRequest) =
      val pl = req.getHeader("pl").get.value
      Future(persons(pl))
    def getPersons = Future(persons.values.toSet)
    def getPersons(ids: Set[PersonID]) = Future(persons.filter((k, v) => ids(k)).values.toSet)

  val emptyQuiz =
    (id: String) => TestEntityRef(QuizEntity.EntityKey, id, testKit.spawn(Behaviors.empty))
  val emptySect =
    (id: String) => TestEntityRef(SectionEditEntity.EntityKey, id, testKit.spawn(Behaviors.empty))

  object eaware extends EntityAware:
    def quiz(id: String) = emptyQuiz(id)
    def section(id: String) = emptySect(id)

  def quiz[R](resp: Resp[R]) =
    new EntityAware:
      val beh = Behaviors.receiveMessage[Quiz.Command] {
        case c: Quiz.CommandWithReply[R] =>
          c.replyTo ! resp
          Behaviors.stopped
        case c: Any =>
          log.error(s"wrong command $c")
          Behaviors.stopped
      }
      def quiz(id: String) = TestEntityRef(QuizEntity.EntityKey, id, testKit.spawn(beh))
      def section(id: String) = emptySect(id)

  val quizList = List(QuizListed("q1", "quiz1", false, p1, Set(p1, p2), Set(p3, p4)))

  object read extends Read:
    def getList() = Future(quizList)

  def get(path: String, personId: String) = Get(s"/v1/$path") ~> addHeader("pl", personId)
  // def post[C](path: String, personId: String, content: C) =
  //  Post(s"/v1/$path", content) ~> addHeader("pl", personId)

  def stdquiz[R](resp: Resp[R]) = HttpFrontend(read, quiz(resp), auth)

  import Resp.*

  "Frontend" should {

    "not authenticate" in {
      get("quiz", "x") ~> HttpFrontend(read, eaware, auth) ~>
        check {
          status shouldBe StatusCodes.Unauthorized
        }
    }

    "return list of quizzes" in {
      get("quiz", p1.id) ~> HttpFrontend(read, eaware, auth) ~>
        check {
          status shouldBe StatusCodes.OK
          responseAs[List[QuizListed]] shouldBe quizList
        }
    }

    "return list of persons" in {
      get("staff", p1.id) ~> HttpFrontend(read, eaware, auth) ~>
        check {
          responseAs[Set[Person]] shouldBe persons.values.toSet
        }
    }

    val create = CreateQuiz("q1", "q1 title", "q1 intro", 10, Set("p1", "p2"), Set("p4", "p5"))

    "create quiz" in {
      val crdet = Quiz.CreateDetails(Set(p1, p3), Set(p4, p5))
      Post("/v1/quiz", create) ~> addHeader("pl", "p2") ~> stdquiz(Good(crdet)) ~>
        check {
          status shouldBe StatusCodes.OK
          responseAs[Quiz.CreateDetails] shouldBe crdet
        }
    }

    "not create quiz with bad input" in {
      Post("/v1/quiz", create) ~> addHeader("pl", "p2") ~>
        stdquiz(Bad(Quiz.tooShortTitle.error())) ~>
        check {
          status shouldBe StatusCodes.ExpectationFailed
          responseAs[Error] shouldBe Quiz.tooShortTitle.error()
        }
    }

  }
