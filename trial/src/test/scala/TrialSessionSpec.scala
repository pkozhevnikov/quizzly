package quizzly.trial

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.*
import akka.http.scaladsl.marshalling.*
import akka.http.scaladsl.model.*
import akka.http.scaladsl.unmarshalling.*
import com.typesafe.config.ConfigFactory
import org.scalatest.*

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.*

import java.time.*

object TrialSessionSpec:
  def config(nodePort: Int, httpPort: Int) =
    ConfigFactory
      .parseString(s"""
    akka.cluster.seed-nodes = ["akka://trialapp@localhost:$nodePort"]
    akka.remote.artery.canonical.port = "$nodePort"
    jdbc-connection-settings.url = "jdbc:h2:mem:trialtest"
    frontend.http.port = $httpPort
    """)
      .withFallback(ConfigFactory.load("application.conf"))
      .resolve

class TrialSessionSpec
    extends featurespec.AnyFeatureSpec,
      GivenWhenThen,
      BeforeAndAfterAll,
      concurrent.Eventually,
      concurrent.IntegrationPatience,
      JsonFormats,
      matchers.should.Matchers:

  import scala.util.Using
  import java.net.ServerSocket
  def freePort(default: Int) = Using(new ServerSocket(0))(_.getLocalPort).getOrElse(default)
  val httpPort = freePort(33035)
  val nodePort = freePort(44046)

  val appSystem = ActorSystem(
    Behaviors.empty,
    "trialapp",
    TrialSessionSpec.config(nodePort, httpPort)
  )
  val clnSystem = ActorSystem(
    Behaviors.empty,
    "trialcln",
    ConfigFactory.parseString("""{akka.actor.provider = "local"}""")
  )

  given ActorSystem[?] = clnSystem
  given ExecutionContext = clnSystem.executionContext
  var nowTime = Instant.parse("2022-11-01T00:00:00Z")
  given (() => Instant) = () => nowTime

  val http = Http()(using clnSystem)

  extension (response: HttpResponse)
    def to[C](using FromResponseUnmarshaller[C]) = Await.result(Unmarshal(response).to[C], 1.second)
  extension (path: String)
    def fullUrl = s"http://localhost:$httpPort/v1/$path"

  given ToEntityMarshaller[Set[String]] = sprayJsonMarshaller[Set[String]]
  def request(req: HttpRequest, as: Person) = Await
    .result(http.singleRequest(req ~> addHeader("p", as.id)), 3.second)
  def post[C](path: String, as: Person, content: C)(using ToEntityMarshaller[C]) = request(
    Post(path.fullUrl, content),
    as
  )
  def patch(path: String, as: Person) = request(Patch(path.fullUrl), as)
  def get(path: String, as: Person) = request(Get(path.fullUrl), as)

  val pers1 = Person("pers1", "pers1 name")
  val pers2 = Person("pers2", "pers2 name")
  val pers3 = Person("pers3", "pers3 name")
  val pers4 = Person("pers4", "pers4 name")
  val pers5 = Person("pers5", "pers5 name")
  val pers6 = Person("pers6", "pers6 name")
  val pers7 = Person("pers7", "pers7 name")
  val all = Map(
    pers1.id -> pers1,
    pers2.id -> pers2,
    pers3.id -> pers3,
    pers4.id -> pers4,
    pers5.id -> pers5,
    pers6.id -> pers6,
    pers7.id -> pers7
  )

  val auth: Auth =
    new:
      def authenticate(req: HttpRequest) =
        all.get(req.getHeader("p").get.value) match
          case Some(p: Person) =>
            Future(p)
          case _ =>
            Future.failed(Exception())
  val quizzes = (1 to 5)
    .map { n =>
      val item1 = Item(
        "i1",
        "i2 intro",
        Statement("i1 def", None),
        List(
          List(Statement("i1 h1 a1", None), Statement("i1 h1 a2", None)),
          List(Statement("i1 h2 a1", None), Statement("i1 h2 a2", None))
        ),
        true,
        List(0, 1)
      )
      val item2 = Item("i2", "i2 intro", Statement("i2 def", None), List(), false, List(1, 2))
      val item3 = Item("i3", "i3 intro", Statement("i3 def", None), List(), false, List(3, 4))
      val section1 = Section("s1", "s1 title", "s1 intro", List(item1, item2))
      val section2 = Section("s2", "s2 title", "s2 intro", List(item3))
      (s"q$n", Quiz(s"q$n", s"q$n title", s"q$n intro", List(section1, section2)))
    }
    .toMap
  val quizreg: QuizRegistry =
    new:
      def get(id: QuizID) = Future(quizzes(id))

  val getExam = Main(appSystem, auth, quizreg)

  override def beforeAll() =
    super.beforeAll()
    (1 to 3).foreach { n =>
      val period = ExamPeriod(
        Instant.parse("2023-01-28T10:00:00Z"),
        Instant.parse("2023-01-30T10:00:00Z")
      )
      val trialLength = 50
      val testees = Set(pers1, pers2, pers3, pers4, pers5)
      getExam(s"exam-$n") ! ExamEntity.Register(s"q$n", period, trialLength, testees)
    }

  override def afterAll() =
    super.afterAll()
    clnSystem.terminate()
    appSystem.terminate()

  import Trial.*

  info("Testee tries a quiz")

  Feature("getting exam info") {

    Scenario("exam info returned") {
      When("exam info is requested")
      val res = get("exam-1", pers1)
      Then("exam info is returned")
      res.status shouldBe StatusCodes.OK
      res.to[ExamInfo] shouldBe
        ExamInfo(
          "q1",
          "q1 title",
          "q1 intro",
          "exam-1",
          Instant.parse("2023-01-28T10:00:00Z"),
          Instant.parse("2023-01-30T10:00:00Z"),
          50
        )
    }

    Scenario("error getting exam info") {
      When("info of notexisting exam is reqeusted")
      val res = get("nonexists", pers1)
      Then("error returned")
      res.status shouldBe StatusCodes.UnprocessableEntity
      res.to[Error] shouldBe examNotFound.error()
    }

    Scenario("access denied for not authenticated user") {
      When("exam info is requested by non-registered used")
      val res = get("exam-1", Person("notexists", ""))
      Then("access denied")
      res.status shouldBe StatusCodes.Unauthorized
    }

  }

  Feature("starting trial") {

    Scenario("not starting if exam not registered") {
      When("start trial of non-existant exam requested")
      val res = patch("notexist", pers1)
      Then("error returned")
      res.status shouldBe StatusCodes.UnprocessableEntity
      res.to[Error] shouldBe examNotFound.error()
    }

    Scenario("not starting by user not included in exam") {
      When("start requested by not included testee")
      val res = patch("exam-1", pers7)
      Then("error returned")
      res.status shouldBe StatusCodes.UnprocessableEntity
      res.to[Error] shouldBe notTestee.error()
    }

    Scenario("successful start") {
      nowTime = Instant.parse("2023-01-29T15:00:00Z")
      When("start trial requested")
      val res = patch("exam-1", pers1)
      Then("trial started")
      res.status shouldBe StatusCodes.OK
      And("first quiz section returned")
      res.to[StartTrialDetails] shouldBe
        StartTrialDetails("pers1-exam-1", pers1, nowTime, 50, quizzes("q1").sections(0).view)
    }

  }

  Feature("item submission") {

    Scenario("not submit if item not found") {
      Given("a trial is started")
      val res0 = patch("exam-1", pers2)
      res0.status shouldBe StatusCodes.OK
      When("a non-existent item is submitted")
      val res = post("pers2-exam-1", pers2, Solution("notexist", List.empty))
      Then("error returned")
      res.status shouldBe StatusCodes.UnprocessableEntity
      res.to[Error] shouldBe itemNotFound.error()
    }

    Scenario("first item submitted") {
      Given("a trial is started")
      patch("exam-1", pers3).status shouldBe StatusCodes.OK
      When("first item submitted")
      val res = post("pers3-exam-1", pers3, Solution("i1", List("0", "1")))
      Then("submission result is returned")
      res.status shouldBe StatusCodes.OK
      res.to[SubmissionResult] shouldBe SubmissionResult(None, false)
    }

    Scenario("submit item switching section") {
      Given("a trial is started")
      And("first item submitted")
      When("second item submitted")
      val res = post("pers3-exam-1", pers3, Solution("i2", List("2", "3")))
      Then("submission result is returned")
      res.status shouldBe StatusCodes.OK
      res.to[SubmissionResult] shouldBe
        SubmissionResult(Some(quizzes("q1").sections(1).view), false)
    }

    Scenario("submit item finalizing trial") {
      Given("a trial is started")
      And("first item submitted")
      And("second item submitted")
      When("third item submitted")
      val res = post("pers3-exam-1", pers3, Solution("i3", List("4", "5")))
      Then("submission result is returned")
      res.status shouldBe StatusCodes.OK
      res.to[SubmissionResult] shouldBe SubmissionResult(None, true)
    }

  }
