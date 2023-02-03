package quizzly.school

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.*
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.testkit.scaladsl.TestEntityRef
import akka.http.scaladsl.marshalling.*
import akka.http.scaladsl.model.*
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import org.scalatest.*

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scaladsl.Behaviors

import java.time.*

class HttpFrontendSpec
    extends wordspec.AnyWordSpec,
      ScalatestRouteTest,
      JsonFormats,
      matchers.should.Matchers:

  val testKit = ActorTestKit("frontendkit", ConfigFactory.empty)
  given ActorSystem[?] = testKit.system

  type NowInstant = () => Instant
  given NowInstant = () => Instant.now()

  val off1 = Official("off1", "off1 name")
  val off2 = Official("off2", "off2 name")
  val off3 = Official("off3", "off3 name")
  val stud1 = Student("stud1", "stud1 name")
  val stud2 = Student("stud2", "stud2 name")
  val stud3 = Student("stud3", "stud3 name")

  val allPersons = Map(
    off1.id -> off1,
    off2.id -> off2,
    off3.id -> off3,
    stud1.id -> stud1,
    stud2.id -> stud2,
    stud3.id -> stud3
  )

  object auth extends Auth:
    def authenticate(req: HttpRequest) =
      allPersons.get(req.getHeader("pl").get.value) match
        case Some(p: Official) =>
          Future(p)
        case _ =>
          Future.failed(Exception())
    def getPersons = Future(allPersons.values.toSet)
    def getPersons(ids: Set[PersonID]) = Future(allPersons.filter((k, v) => ids(k)).values.toSet)
    def getPerson(id: PersonID) = Future(allPersons.get(id))

  object eaware extends EntityAware:
    def exam(id: String) = TestEntityRef(ExamEntity.EntityKey, id, testKit.spawn(Behaviors.empty))
    def fact(id: String) = TestEntityRef(QuizFact.EntityKey, id, testKit.spawn(Behaviors.empty))

  object read extends Read:
    def examList()(using ExecutionContext) = Future(List(exam1, exam2))
    def testees(examId: ExamID)(using ExecutionContext) =
      examId match
        case "e1" =>
          Future(List(off2, stud1, stud2))
        case _ =>
          Future(List(off3, stud1, stud2, stud3))
    def quizList()(using ExecutionContext) = Future(List(quiz1, quiz2, quiz3))

  given ToEntityMarshaller[Set[String]] = sprayJsonMarshaller[Set[String]]

  def get(path: String, person: Person) = Get(s"/v1/$path") ~> addHeader("pl", person.id)
  def post(path: String, person: Person) = Post(s"/v1/$path") ~> addHeader("pl", person.id)
  def delete(path: String, person: Person) = Delete(s"/v1/$path") ~> addHeader("pl", person.id)
  def post[C](path: String, person: Person, content: C)(using ToEntityMarshaller[C]) =
    Post(s"/v1/$path", content) ~> addHeader("pl", person.id)
  def put[C](path: String, person: Person, content: C)(using ToEntityMarshaller[C]) =
    Put(s"/v1/$path", content) ~> addHeader("pl", person.id)
  def patch[C](path: String, person: Person, content: C)(using ToEntityMarshaller[C]) =
    Patch(s"/v1/$path", content) ~> addHeader("pl", person.id)
  def patch(path: String, person: Person) = Patch(s"/v1/$path") ~> addHeader("pl", person.id)

  val dateTime1 = ZonedDateTime.parse("2023-01-10T10:00:00Z")

  val exam1 = ExamView(
    "e1",
    QuizRef("q1", "q1 title"),
    ExamPeriod(dateTime1, dateTime1.plusDays(1)),
    off1,
    "Upcoming",
    None,
    45,
    60,
    dateTime1.minusDays(1)
  )
  val exam2 = ExamView(
    "e2",
    QuizRef("q2", "q2 title"),
    ExamPeriod(dateTime1.plusWeeks(1), dateTime1.plusWeeks(1).plusDays(1)),
    off1,
    "Pending",
    None,
    60,
    70,
    dateTime1.plusWeeks(1).minusDays(1)
  )

  val quiz1 = QuizListed("q1", "q1 title", false, true, false, false, 45)
  val quiz2 = QuizListed("q2", "q2 title", false, true, false, false, 45)
  val quiz3 = QuizListed("q3", "q3 title", false, false, false, false, 45)

  val quizList = List[QuizListed](quiz1, quiz2, quiz3)

  def exam[R](expectedId: ExamID, resp: Resp[R]) =
    new EntityAware:
      val beh = Behaviors.receiveMessage[Exam.Command] { case c: Exam.CommandWithReply[R] =>
        c.replyTo ! resp
        Behaviors.stopped
      }
      def exam(id: String) =
        id shouldBe expectedId
        TestEntityRef(ExamEntity.EntityKey, id, testKit.spawn(beh))
      def fact(id: String) = TestEntityRef(QuizFact.EntityKey, id, testKit.spawn(Behaviors.empty))
  def exam[R](expectedId: ExamID, beh: Behavior[Exam.Command]) =
    new EntityAware:
      def exam(id: String) =
        id shouldBe expectedId
        TestEntityRef(ExamEntity.EntityKey, id, testKit.spawn(beh))
      def fact(id: String) = TestEntityRef(QuizFact.EntityKey, id, testKit.spawn(Behaviors.empty))

  def fact[R](expectedId: QuizID, resp: Resp[R]) =
    new EntityAware:
      val beh = Behaviors.receiveMessage[QuizFact.Command] { case c: QuizFact.CommandWithReply[R] =>
        c.replyTo ! resp
        Behaviors.stopped
      }
      def fact(id: String) =
        id shouldBe expectedId
        TestEntityRef(QuizFact.EntityKey, id, testKit.spawn(beh))
      def exam(id: String) = TestEntityRef(ExamEntity.EntityKey, id, testKit.spawn(Behaviors.empty))
  def fact[R](expectedId: QuizID, beh: Behavior[QuizFact.Command]) =
    new EntityAware:
      def fact(id: String) =
        id shouldBe expectedId
        TestEntityRef(QuizFact.EntityKey, id, testKit.spawn(beh))
      def exam(id: String) = TestEntityRef(ExamEntity.EntityKey, id, testKit.spawn(Behaviors.empty))

  def stdexam[R](expectedId: ExamID, resp: Resp[R]) = HttpFrontend(
    read,
    exam(expectedId, resp),
    auth
  )
  def stdexam[R](expectedId: ExamID, beh: Behavior[Exam.Command])(using NowInstant) = HttpFrontend(
    read,
    exam(expectedId, beh),
    auth
  )
  def stdfact[R](expectedId: QuizID, resp: Resp[R]) = HttpFrontend(
    read,
    fact(expectedId, resp),
    auth
  )
  def stdfact[R](expectedId: QuizID, beh: Behavior[QuizFact.Command]) = HttpFrontend(
    read,
    fact(expectedId, beh),
    auth
  )

  import Resp.*

  import Exam.*

  "quiz" when {
    "GET" should {
      "not authenticate" in {
        get("quiz", stud1) ~> HttpFrontend(read, eaware, auth) ~>
          check {
            status shouldBe StatusCodes.Unauthorized
          }
      }
      "return quiz list" in {
        get("quiz", off1) ~> HttpFrontend(read, eaware, auth) ~>
          check {
            status shouldBe StatusCodes.OK
            responseAs[List[QuizListed]] shouldBe quizList
          }
      }
    }

  }

  "persons" when {
    "GET" should {
      "return person list" in {
        get("persons", off1) ~> HttpFrontend(read, eaware, auth) ~>
          check {
            status shouldBe StatusCodes.OK
            responseAs[List[Person]] should contain theSameElementsAs
              Set(off1, off2, off3, stud1, stud2, stud3)
          }
      }
    }
  }

  "exam" when {
    "GET" should {
      "return exam list" in {
        get("exam", off1) ~> HttpFrontend(read, eaware, auth) ~>
          check {
            status shouldBe StatusCodes.OK
            responseAs[List[ExamView]] should contain theSameElementsAs Set(exam1, exam2)
          }
      }
    }
    "POST" should {
      "reject creation" in {
        val create = CreateExam(
          "e1",
          "q1",
          45,
          60,
          ZonedDateTime.parse("2023-01-01T10:10:00Z"),
          ZonedDateTime.parse("2023-01-02T10:10:00Z"),
          Set.empty
        )
        post("exam", off1, create) ~> stdexam("e1", Bad(badExamPeriod.error())) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe badExamPeriod.error()
          }
      }
      "create exam" in {
        val det = CreateExamDetails(dateTime1, off1)
        val beh = Behaviors.receiveMessage[Exam.Command] {
          case c: Create =>
            c.replyTo ! Good(det)
            Behaviors.stopped
          case _ =>
            Behaviors.stopped
        }
        val create = CreateExam("e1", "q1", 45, 60, dateTime1, dateTime1, Set(off1.id, stud1.id))
        post("exam", off1, create) ~> stdexam("e1", beh) ~>
          check {
            status shouldBe StatusCodes.OK
            responseAs[CreateExamDetails] shouldBe det
          }
      }
    }
  }

  "exam/{id}" when {
    "GET" should {
      "return testees of exam" in {
        get("exam/e1", off1) ~> HttpFrontend(read, eaware, auth) ~>
          check {
            status shouldBe StatusCodes.OK
            responseAs[List[Person]] should contain theSameElementsAs Set(off2, stud1, stud2)
          }
      }
    }
    "POST" should {
      "change trial attrs" in {
        val beh = Behaviors.receiveMessage[Exam.Command] {
          case SetTrialAttrs(93, 85, replyTo) =>
            replyTo ! Resp.OK
            Behaviors.stopped
          case _ =>
            Behaviors.stopped
        }
        post("exam/e1", off1, ChangeTrialAttrs(93, 85)) ~> stdexam("e1", beh) ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "respond with error" in {
        post("exam/e1", off1, ChangeTrialAttrs(93, 85)) ~>
          stdexam("e1", Bad(badTrialLength.error())) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe badTrialLength.error()
          }
      }
    }

    "PUT" should {
      "include testees" in {
        val beh = Behaviors.receiveMessage[Exam.Command] {
          case IncludeTestees(set, replyTo) if set == Set(off3, stud2) =>
            replyTo ! Good(Set(stud2))
            Behaviors.stopped
          case _ =>
            Behaviors.stopped
        }
        put("exam/e1", off1, Set(off3.id, stud2.id)) ~> stdexam("e1", beh) ~>
          check {
            status shouldBe StatusCodes.OK
            responseAs[List[Person]] should contain only (stud2)
          }
      }
      "respond with error" in {
        put("exam/e1", off1, Set(off3.id)) ~> stdexam("e1", Bad(illegalState.error())) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe illegalState.error()
          }
      }
    }

    "PATCH" should {
      "exclude testees" in {
        val beh = Behaviors.receiveMessage[Exam.Command] {
          case ExcludeTestees(set, replyTo) if set == Set(off3, stud2) =>
            replyTo ! Good(Set(stud2))
            Behaviors.stopped
          case _ =>
            Behaviors.stopped
        }
        patch("exam/e1", off1, Set(off3.id, stud2.id)) ~> stdexam("e1", beh) ~>
          check {
            status shouldBe StatusCodes.OK
            responseAs[List[Person]] should contain only (stud2)
          }
      }
      "respond with error" in {
        patch("exam/e1", off1, Set(off3.id)) ~> stdexam("e1", Bad(illegalState.error())) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe illegalState.error()
          }
      }
    }

    "DELETE" should {
      "cancel exam" in {
        val now = Instant.parse("2023-01-02T01:02:03Z")
        val beh = Behaviors.receiveMessage[Exam.Command] {
          case Cancel(`now`, replyTo) =>
            replyTo ! Resp.OK
            Behaviors.stopped
          case _ =>
            Behaviors.stopped
        }
        delete("exam/e1", off1) ~> stdexam("e1", beh)(using () => now) ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "respond with error" in {
        delete("exam/e1", off1) ~> stdexam("e1", Bad(examNotFound.error())) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe examNotFound.error()
          }
      }
    }

  }

  "quiz/{id}" when {

    "PATCH" should {
      "publish responds with error" in {
        patch("quiz/q1", off1) ~> stdfact("q1", Bad(QuizFact.isUsed.error())) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe QuizFact.isUsed.error()
          }
      }
      "publish the quiz" in {
        val beh = Behaviors.receiveMessage[QuizFact.Command] {
          case QuizFact.Publish(replyTo) =>
            replyTo ! Resp.OK
            Behaviors.stopped
          case _ =>
            Behaviors.stopped
        }
        patch("quiz/q1", off1) ~> stdfact("q1", beh) ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
    }

    "DELETE" should {
      "unpublish responds with error" in {
        delete("quiz/q1", off1) ~> stdfact("q1", Bad(QuizFact.isNotPublished.error())) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe QuizFact.isNotPublished.error()
          }
      }
      "unpublish quiz" in {
        val beh = Behaviors.receiveMessage[QuizFact.Command] {
          case QuizFact.Unpublish(replyTo) =>
            replyTo ! Resp.OK
            Behaviors.stopped
          case _ =>
            Behaviors.stopped
        }
        delete("quiz/q1", off1) ~> stdfact("q1", beh) ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
    }

  }
