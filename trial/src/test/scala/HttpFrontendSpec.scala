package quizzly.trial

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

  val pers1 = Person("pers1", "pers1 name")
  val pers2 = Person("pers2", "pers2 name")

  val allPersons = Map(
    pers1.id -> pers1,
    pers2.id -> pers2
  )

  object auth extends Auth:
    def authenticate(req: HttpRequest) =
      allPersons.get(req.getHeader("pl").get.value) match
        case Some(p: Person) =>
          Future(p)
        case _ =>
          Future.failed(Exception())

  object eaware extends EntityAware:
    def exam(id: ExamID) = TestEntityRef(ExamEntity.EntityKey, id, testKit.spawn(Behaviors.empty))
    def trial(id: TrialID) = TestEntityRef(TrialEntity.EntityKey, id, testKit.spawn(Behaviors.empty))

  given ToEntityMarshaller[Set[String]] = sprayJsonMarshaller[Set[String]]

  def get(path: String, person: Person) = Get(s"/v1/$path") ~> addHeader("pl", person.id)
  def post[C](path: String, person: Person, content: C)(using ToEntityMarshaller[C]) =
    Post(s"/v1/$path", content) ~> addHeader("pl", person.id)
  def patch(path: String, person: Person) = Patch(s"/v1/$path") ~> addHeader("pl", person.id)

  val dateTime1 = ZonedDateTime.parse("2023-01-10T10:00:00Z")

  object quizreg extends QuizRegistry:
    def get(id: QuizID) = Future(quiz)

  val item1 = Item(
    "i1", "i2 intro", Statement("i1 def", None),
      List(List(Statement("i1 h1 a1", None), Statement("i1 h1 a2", None)),
        List(Statement("i1 h2 a1", None), Statement("i1 h2 a2", None))),
      true, List(0, 1))
  val item2 = Item(
    "i2", "i2 intro", Statement("i2 def", None),
      List(), false, List(1, 2))
  val item3 = Item(
    "i3", "i3 intro", Statement("i3 def", None),
      List(), false, List(3, 4))
  val section1 = Section("s1", "s1 title", "s1 intro", List(item1, item2))
  val section2 = Section("s2", "s2 title", "s2 intro", List(item3))
  val quiz = Quiz("q1", "q1 title", "q1 intro", List(section1, section2))

  def exam[R](expectedId: ExamID, resp: Resp[R]) =
    new EntityAware:
      val beh = Behaviors.receiveMessage[ExamEntity.Command] { case c: ExamEntity.CommandWithReply[R] =>
        c.replyTo ! resp
        Behaviors.stopped
      }
      def exam(id: String) =
        id shouldBe expectedId
        TestEntityRef(ExamEntity.EntityKey, id, testKit.spawn(beh))
      def trial(id: String) = TestEntityRef(TrialEntity.EntityKey, id, testKit.spawn(Behaviors.empty))
  def exam[R](expectedId: ExamID, beh: Behavior[ExamEntity.Command]) =
    new EntityAware:
      def exam(id: String) =
        id shouldBe expectedId
        TestEntityRef(ExamEntity.EntityKey, id, testKit.spawn(beh))
      def trial(id: String) = TestEntityRef(TrialEntity.EntityKey, id, testKit.spawn(Behaviors.empty))

  def trial[R](expectedId: TrialID, resp: Resp[R]) =
    new EntityAware:
      val beh = Behaviors.receiveMessage[Trial.Command] { case c: Trial.CommandWithReply[R] =>
        c.replyTo ! resp
        Behaviors.stopped
      }
      def trial(id: String) =
        id shouldBe expectedId
        TestEntityRef(TrialEntity.EntityKey, id, testKit.spawn(beh))
      def exam(id: String) = TestEntityRef(ExamEntity.EntityKey, id, testKit.spawn(Behaviors.empty))
  def trial[R](expectedId: TrialID, beh: Behavior[Trial.Command]) =
    new EntityAware:
      def trial(id: String) =
        id shouldBe expectedId
        TestEntityRef(TrialEntity.EntityKey, id, testKit.spawn(beh))
      def exam(id: String) = TestEntityRef(ExamEntity.EntityKey, id, testKit.spawn(Behaviors.empty))

  def stdexam[R](expectedId: ExamID, resp: Resp[R]) = HttpFrontend(
    quizreg,
    exam(expectedId, resp),
    auth
  )
  def stdexam[R](expectedId: ExamID, beh: Behavior[ExamEntity.Command])(using NowInstant) = HttpFrontend(
    quizreg,
    exam(expectedId, beh),
    auth
  )
  def stdtrial[R](expectedId: TrialID, resp: Resp[R]) = HttpFrontend(
    quizreg,
    trial(expectedId, resp),
    auth
  )
  def stdtrial[R](expectedId: TrialID, beh: Behavior[Trial.Command]) = HttpFrontend(
    quizreg,
    trial(expectedId, beh),
    auth
  )

  import Resp.*

  import Trial.*

  "{id}" when {

    "GET" should {
      "not authenticate" in {
        get("exam-1", Person("notexist", "")) ~> HttpFrontend(quizreg, eaware, auth) ~> check {
          status shouldBe StatusCodes.Unauthorized
        }
      }
      "exam not found" in {
        get("exam-2", pers1) ~> stdexam("exam-2", Bad(examNotFound.error())) ~> check {
          status shouldBe StatusCodes.UnprocessableEntity
          responseAs[Error] shouldBe examNotFound.error()
        }
      }
      "return exam info" in {
        val info = ExamInfo("q1", "q1 title", "q1 intro", "exam-1", 
          Instant.parse("2023-01-29T10:00:00Z"), Instant.parse("2023-01-30T10:00:00Z"), 55)
        get("exam-1", pers1) ~> stdexam("exam-1", Good(ExamEntity.ExamAttrs("exam-1", "q1", 
            info.start, info.end, 55))) ~> check {
          status shouldBe StatusCodes.OK
          responseAs[ExamInfo] shouldBe info
        }
      }
    }

    "PATCH" should {

      "respond with error" in {
        patch("exam-1", pers1) ~> stdtrial("pers1-exam-1", Bad(notTestee.error())) ~> check {
          status shouldBe StatusCodes.UnprocessableEntity
          responseAs[Error] shouldBe notTestee.error()
        }
      }

      "return start details" in {
        val det = StartTrialDetails("pers1-exam-1", pers1, Instant.parse("2023-01-29T10:00:00Z"),
          55, section1.view)
        val beh = Behaviors.receiveMessage[Command] {
          case s: Start =>
            s.replyTo ! Good(det)
            Behaviors.stopped
          case _ =>
            Behaviors.stopped
        }
        patch("exam-1", pers1) ~> stdtrial("pers1-exam-1", beh) ~> check {
          status shouldBe StatusCodes.OK
          responseAs[StartTrialDetails] shouldBe det
        }
      }
    }
    
    "POST" should {

      "respond with error" in {
        val subm = Solution("i1", List("a1", "a2"))
        post("t1", pers1, subm) ~> stdtrial("t1", Bad(trialFinalized.error())) ~> check {
          status shouldBe StatusCodes.UnprocessableEntity
          responseAs[Error] shouldBe trialFinalized.error()
        }
      }

      "return submission result" in {
        val res = SubmissionResult(Some(section1.view), false)
        val beh = Behaviors.receiveMessage[Command] {
          case s: Submit =>
            s.replyTo ! Good(res)
            Behaviors.stopped
          case _ =>
            Behaviors.stopped
        }
        val subm = Solution("i1", List("a1", "a2"))
        post("t1", pers1, subm) ~> stdtrial("t1", beh) ~> check {
          status shouldBe StatusCodes.OK
          responseAs[SubmissionResult] shouldBe res
        }
      }

    }

  }
