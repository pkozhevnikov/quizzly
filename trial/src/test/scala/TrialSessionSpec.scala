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
    akka.cluster.seed-nodes = ["akka://app@localhost:$nodePort"]
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
  given (() => Instant) = () => Instant.parse("2022-11-01T00:00:00Z")

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
  val all = Map(pers1.id -> pers1, pers2.id -> pers2, pers3.id -> pers3)

  val item1 = ItemView(
    "i1",
    "i1 intro",
    Statement("i1 def", None),
    List(Statement("i1 h1", None), Statement("i1 h2", None)),
    false
  )
  val item2 = ItemView(
    "i2",
    "i2 intro",
    Statement("i2 def", None),
    List(Statement("i2 h1", None), Statement("i2 h2", None)),
    true
  )
  val item3 = ItemView(
    "i3",
    "i3 intro",
    Statement("i3 def", None),
    List(Statement("i3 h1", None), Statement("i3 h2", None)),
    false
  )
  val section1 = SectionView("section1 title", "section1 intro", List(item1, item2))
  val section2 = SectionView("section2 title", "section2 intro", List(item3))

  val auth: Auth =
    new:
      def authenticate(req: HttpRequest) =
        all.get(req.getHeader("p").get.value) match
          case Some(p: Person) =>
            Future(p)
          case _ =>
            Future.failed(Exception())

  // val getFact = Main(appSystem, auth)

  override def beforeAll() = super.beforeAll()

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
          55
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
      val res = patch("exam-3", pers1)
      Then("error returned")
      res.status shouldBe StatusCodes.UnprocessableEntity
      res.to[Error] shouldBe examNotFound.error()
    }

    Scenario("not starting by user not included in exam") {
      When("start requested by not included testee")
      val res = patch("exam-1", pers3)
      Then("error returned")
      res.status shouldBe StatusCodes.UnprocessableEntity
      res.to[Error] shouldBe notTestee.error()
    }

    Scenario("successful start") {
      When("start trial requested")
      val res = patch("exam-1", pers1)
      Then("trial started")
      res.status shouldBe StatusCodes.OK
      And("first quiz section returned")
      res.to[SectionView] shouldBe section1
    }

  }

  Feature("item submission") {
    
    Scenario("not submit if item not found") {
      Given("a trial is started")
      val res0 = patch("exam-1", pers2)
      res0.status shouldBe StatusCodes.OK
      When("a non-existent item is submitted")
      val res = post("exam-1", pers2, Solution("notexist", List.empty))
      Then("error returned")
      res.status shouldBe StatusCodes.UnprocessableEntity
      res.to[Error] shouldBe itemNotFound.error()
    }

    Scenario("first item submitted") {
      Given("a trial is started")
      patch("exam-1", pers3).status shouldBe StatusCodes.OK
      When("first item submitted")
      val res = post("exam-1", pers3, Solution("i1", List("0", "1")))
      Then("submission result is returned")
      res.status shouldBe StatusCodes.OK
      res.to[SubmissionResult] shouldBe SubmissionResult(None, false)
    }

  }
