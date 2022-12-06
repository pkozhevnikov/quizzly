package quizzly.author

import akka.actor.typed.*
import scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.testkit.scaladsl.TestEntityRef

import akka.http.scaladsl.model.*
import akka.http.scaladsl.marshalling.*
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

  val fullquiz = FullQuiz(
    id = "q1",
    title = "q1 title",
    intro = "",
    curator = p1,
    authors = Set(p2, p3),
    inspectors = Set(p4, p5),
    recommendedLength = 70,
    readinessSigns = Set(p2, p3),
    approvals = Set(p4),
    disapprovals = Set(p5),
    obsolete = false,
    sections = List(
      Section(
        "q1-1",
        "q1-1 title",
        "q1-1 intro",
        List(
          Item(
            sc = "1",
            intro = "item intro",
            definition = Statement("item text", None),
            hints = List(List(Statement("hint 1", None), Statement("hint 2", None))),
            hintsVisible = true,
            solutions = List(0)
          )
        )
      )
    ),
    state = Quiz.State.COMPOSING
  )

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

  def quiz[R](expectedId: QuizID, resp: Resp[R]) =
    new EntityAware:
      val beh = Behaviors.receiveMessage[Quiz.Command] { case c: Quiz.CommandWithReply[R] =>
        c.replyTo ! resp
        Behaviors.stopped
      }
      def quiz(id: String) =
        id shouldBe expectedId
        TestEntityRef(QuizEntity.EntityKey, id, testKit.spawn(beh))
      def section(id: String) = emptySect(id)

  def quiz(expectedId: QuizID, beh: Behavior[Quiz.Command]) =
    new EntityAware:
      def quiz(id: QuizID) =
        id shouldBe expectedId
        TestEntityRef(QuizEntity.EntityKey, id, testKit.spawn(beh))
      def section(id: SC) = emptySect(id)

  val quizList = List(
    QuizListed("q1", "quiz1", false, p1, Set(p1, p2), Set(p3, p4), Quiz.State.COMPOSING)
  )

  object read extends Read:
    def getList() = Future(quizList)

  def get(path: String, personId: PersonID) = Get(s"/v1/$path") ~> addHeader("pl", personId)
  def delete(path: String, personId: PersonID) = Delete(s"/v1/$path") ~> addHeader("pl", personId)
  def head(path: String, personId: PersonID) = Head(s"/v1/$path") ~> addHeader("pl", personId)
  def post[C](path: String, personId: PersonID, content: C)(using ToEntityMarshaller[C]) =
    Post(s"/v1/$path", content) ~> addHeader("pl", personId)
  def put[C](path: String, personId: PersonID, content: C)(using ToEntityMarshaller[C]) =
    Put(s"/v1/$path", content) ~> addHeader("pl", personId)
  def patch[C](path: String, personId: PersonID, content: C)(using ToEntityMarshaller[C]) =
    Patch(s"/v1/$path", content) ~> addHeader("pl", personId)

  def stdquiz[R](expectedId: QuizID, resp: Resp[R]) = HttpFrontend(
    read,
    quiz(expectedId, resp),
    auth
  )
  def spcquiz(expectedId: QuizID, beh: Behavior[Quiz.Command]) = HttpFrontend(
    read,
    quiz(expectedId, beh),
    auth
  )

  import Resp.*

  "quiz" when {
    "GET" should {
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
    }
    "POST" should {
      val create = CreateQuiz("q1", "q1 title", "q1 intro", 10, Set("p1", "p3"), Set("p4", "p5"))
      "create quiz" in {
        val auths = Set(p1, p3)
        val insps = Set(p4, p5)
        val crdet = Quiz.CreateDetails(auths, insps)
        val route = spcquiz(
          "q1",
          Behaviors.receiveMessage {
            case Quiz.Create("q1", "q1 title", "q1 intro", `p2`, `auths`, `insps`, 10, replyTo) =>
              replyTo ! Good(crdet)
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        post("quiz", p2.id, create) ~> route ~>
          check {
            status shouldBe StatusCodes.OK
            responseAs[Quiz.CreateDetails] shouldBe crdet
          }
      }
      "not create quiz with bad input" in {
        post("quiz", p2.id, create) ~> stdquiz("q1", Bad(Quiz.tooShortTitle.error())) ~>
          check {
            status shouldBe StatusCodes.ExpectationFailed
            responseAs[Error] shouldBe Quiz.tooShortTitle.error()
          }
      }
    }
  }

  "staff" when {
    "GET" should {
      "return list of persons" in {
        get("staff", p1.id) ~> HttpFrontend(read, eaware, auth) ~>
          check {
            responseAs[Set[Person]] shouldBe persons.values.toSet
          }
      }
    }
  }

  "quiz/{id}" when {
    "GET" should {
      "return full quiz" in {
        get("quiz/q1", p1.id) ~> stdquiz("q1", Good(fullquiz)) ~>
          check {
            status shouldBe StatusCodes.OK
            responseAs[FullQuiz] shouldBe fullquiz
          }
      }
    }
    "PUT" should {
      val updt = UpdateQuiz("q1 title+", "q1 intro+", 55)
      "update quiz" in {
        val route = spcquiz(
          "q1",
          Behaviors.receiveMessage {
            case Quiz.Update("q1 title+", "q1 intro+", 55, `p1`, replyTo) =>
              replyTo ! Resp.OK
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        put("quiz/q1", p1.id, updt) ~> route ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "not update quiz with error" in {
        val err = Quiz.quizNotFound.error() + "q1"
        put("quiz/q1", p1.id, updt) ~> stdquiz("q1", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.ExpectationFailed
            responseAs[Error] shouldBe err
          }
      }
    }
    "POST" should {
      val addsec = CreateSection("section title")
      "add section" in {
        val route = spcquiz(
          "q1",
          Behaviors.receiveMessage {
            case Quiz.AddSection("section title", `p2`, replyTo) =>
              replyTo ! Good("1")
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        post("quiz/q1", p2.id, addsec) ~> route ~>
          check {
            status shouldBe StatusCodes.OK
            responseAs[String] shouldBe "1"
          }
      }
      "not add section with error" in {
        val err = Quiz.tooShortTitle.error()
        post("quiz/q1", p2.id, addsec) ~> stdquiz("q1", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.ExpectationFailed
            responseAs[Error] shouldBe err
          }
      }
    }
    "HEAD" should {
      "own section" in {
        val route = spcquiz(
          "qx",
          Behaviors.receiveMessage {
            case Quiz.OwnSection("qx-1", `p3`, replyTo) =>
              replyTo ! Resp.OK
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        head("quiz/qx?sc=qx-1", p3.id) ~> route ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "not own section" in {
        val err = Quiz.sectionNotFound.error() + "qx-1"
        head("quiz/qx?sc=qx-1", p3.id) ~> stdquiz("qx", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.ExpectationFailed
            responseAs[Error] shouldBe err
          }
      }
    }
  }
