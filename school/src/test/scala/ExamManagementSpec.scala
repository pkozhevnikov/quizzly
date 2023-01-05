package quizzly.school

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

object ExamManagementSpec:
  def config(nodePort: Int, httpPort: Int) =
    ConfigFactory
      .parseString(s"""
    akka.cluster.seed-nodes = ["akka://app@localhost:$nodePort"]
    akka.remote.artery.canonical.port = "$nodePort"
    jdbc-connection-settings.url = "jdbc:h2:mem:app"
    frontend.http.port = $httpPort
    school {
      preparationPeriodHours = 24
      trialLengthMinutes = {
        min = 10
        max = 90
      }
    }
    """)
      .withFallback(ConfigFactory.load("application.conf"))
      .resolve

class ExamManagementSpec
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
  val httpPort = freePort(33032)
  val nodePort = freePort(44043)

  val appSystem = ActorSystem(Behaviors.empty, "app", ExamManagementSpec.config(nodePort, httpPort))
  val clnSystem = ActorSystem(
    Behaviors.empty,
    "cln",
    ConfigFactory.parseString("""{akka.actor.provider = "local"}""")
  )

  given ActorSystem[?] = clnSystem
  given ExecutionContext = clnSystem.executionContext

  val http = Http()(using clnSystem)

  override def afterAll() =
    clnSystem.terminate()
    appSystem.terminate()

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
  def put[C](path: String, as: Person, content: C)(using ToEntityMarshaller[C]) = request(
    Put(path.fullUrl, content),
    as
  )
  def patch[C](path: String, as: Person, content: C)(using ToEntityMarshaller[C]) = request(
    Patch(path.fullUrl, content),
    as
  )
  def get(path: String, as: Person) = request(Get(path.fullUrl), as)
  def delete(path: String, as: Person) = request(Delete(path.fullUrl), as)
  def patch(path: String, as: Person) = request(Patch(path.fullUrl), as)
  def post(path: String, as: Person) = request(Post(path.fullUrl), as)
  def put(path: String, as: Person) = request(Put(path.fullUrl), as)

  val off1 = Official("off1", "off1 name")
  val off2 = Official("off2", "off2 name")
  val off3 = Official("off3", "off3 name")
  val off4 = Official("off4", "off4 name")
  val off5 = Official("off5", "off5 name")
  val stud1 = Student("stud1", "stud1 name")
  val stud2 = Student("stud2", "stud2 name")
  val stud3 = Student("stud3", "stud3 name")
  val stud4 = Student("stud4", "stud4 name")
  val stud5 = Student("stud5", "stud5 name")
  val all = Map(
    off1.id -> off1,
    off2.id -> off2,
    off3.id -> off3,
    off4.id -> off4,
    off5.id -> off5,
    stud1.id -> stud1,
    stud2.id -> stud2,
    stud3.id -> stud3,
    stud4.id -> stud4,
    stud5.id -> stud5
  )

  val auth: Auth = new:
    def authenticate(req: HttpRequest) =
      all.get(req.getHeader("p").get.value) match
        case Some(p: Official) =>
          Future(p)
        case _ =>
          Future.failed(Exception())
    def getPersons = Future(all.values.toSet)
    def getPersons(ids: Set[PersonID]) = Future(all.filter((k, v) => ids(k)).values.toSet)
    def getPerson(id: PersonID) = Future(all.get(id))

  Main(appSystem, auth)

  import Exam.*

  info("Officials create, modify and cancel exams")

  Feature("person list") {

    Scenario("getting person list") {
      When("person list is requested by official")
      val res = get("persons", off1)
      Then("list of all persons is returned")
      res.status shouldBe StatusCodes.OK
      res.to[List[Person]] should contain theSameElementsAs
        Set(off1, off2, off3, off4, off5, stud1, stud2, stud3, stud4, stud5)
    }

    Scenario("access denied for not officials") {
      When("person list is requested by a student")
      val res = get("persons", stud1)
      Then("access denied")
      res.status shouldBe StatusCodes.Unauthorized
    }

  }

  Feature("exams and quizzes listing") {

    Scenario("getting quiz list") {
      When("quiz list is requested")
      val res = get("quiz", off1)
      Then("quiz list is returned")
      res.status shouldBe StatusCodes.OK
      val list = res.to[List[QuizListed]]
      list.size shouldBe 3
      list.find(_.id == "q2").get shouldBe QuizListed("q2", "q2 title", false, true, false, false)
    }

  }

  /*
  val createExam = CreateExam(
    "e1",
    "q1",
    45,
    ZonedDateTime.parse("2023-01-10T10:00:00Z"),
    ZonedDateTime.parse("2013-01-10T15:00:00Z"),
    Set(off2.id, stud1.id, stud3.id)
  )

  Feature("Create an Exam") {

    Scenario("Exam is created if all input is correct") {
      Given("specified unique Exam identifier")
      And("specified other attributes")
      val req = createExam
      When("create request is performed")
      val res = post("exam", off1, req)
      Then("new Exam is created")
      res.status shouldBe StatusCodes.OK
      And("its prestart time is correct")
      val details = res.to[CreateExamDetails]
      details.preparationStart shouldBe ZonedDateTime.parse("2013-01-09T10T10:00:00Z")
      And("I am a Host of this Exam")
      details.host shouldBe off1
    }

    Scenario("Exam is not created if identifier is not unique") {
      post("exam", off1, createExam.copy(id = "ex"))
      Given("specified identifier that already exists")
      val req = createExam.copy(id = "ex")
      When("create request is performed")
      val res = post("exam", off1, req)
      Then("new Exam is not created")
      res.status shouldBe StatusCodes.UnprocessableEntity
      And("response contains detailed reason")
      res.to[Error] shouldBe examAlreadyExists.error() + "ex"
    }
  }

  Feature("exam listing") {
    Given("exams registered")
    post("exam", off1, createExam.copy(id = "e7"))
    When("exam list is requested")
    val res = get("exam", off2)
    Then("exam list is returned")
    res.status shouldBe StatusCodes.OK
    val list = res.to[List[ExamView]]
    And("correct exam view is present")
    list.find(_.id == "e7").get shouldBe
      ExamView(
        "e7",
        QuizRef("q1", "q1 title"),
        ExamPeriod(
          ZonedDateTime.parse("2023-01-10T10:00:00Z"),
          ZonedDateTime.parse("2023-01-10T15:00:00Z")
        ),
        off1,
        "Pending",
        None,
        45,
        ZonedDateTime.parse("2023-01-09T10:00:00Z")
      )
  }

  Feature("Include/exclude a Testee") {

    Scenario("Testee added") {
      Given("existing Exam")
      val res0 = post("exam", off1, createExam.copy(id = "e3"))
      And("I am a Host")
      res0.to[CreateExamDetails].host shouldBe off1
      And("specified a user as a new Testee")
      val include = Set(stud2.id, stud4.id)
      When("'add testee' request is performed")
      val res = put("exam/e3", off1, include)
      Then("specified user is on Exam list as a Testee")
      res.status shouldBe StatusCodes.OK
      res.to[List[Person]] should contain theSameElementsAs Set(stud2, stud4)
      val res2 = get("exam/e3", off1)
      res2.status shouldBe StatusCodes.OK
      res2.to[List[Person]] should contain theSameElementsAs Set(off2, stud1, stud3, stud2, stud4)
    }

    Scenario("Testee removed") {
      Given("existing Exam")
      val res0 = post("exam", off1, createExam.copy(id = "e4"))
      And("I am a Host")
      res0.to[CreateExamDetails].host shouldBe off1
      And("a specific Testee is on list")
      get("exam/e4", off1).to[List[Person]] should contain theSameElementsAs Set(off2, stud1, stud3)
      When("'remove testee' request is performed")
      val res = patch("exam/e4", off1, Set(off2.id, stud3.id))
      Then("specified Testee is no longer on Exam list")
      res.status shouldBe StatusCodes.OK
      res.to[List[Person]] should contain theSameElementsAs Set(off2, stud3)
      get("exam/e4", off1).to[List[Person]] should contain only (stud1)
    }
  }

  Feature("Modify Trial Length") {
    Scenario("Trial Length is changed") {
      Given("existing Exam")
      post("exam", off1, createExam.copy(id = "e5"))
      When("'change trial length' request is performed")
      val res = post("exam/e5", off1, ChangeLength(54))
      Then("specified Trial Length is set on the Exam")
      res.status shouldBe StatusCodes.NoContent
      val ex = get("exam", off1).to[List[ExamView]].find(_.id == "e5").get
      ex.trialLength shouldBe 54
    }
  }

  Feature("Cancel Exam") {
    Scenario("Exam is cancelled") {
      Given("existing Exam")
      post("exam", off1, createExam.copy(id = "e6"))
      When("'cancel exam' request is performed")
      val res = delete("exam/e6", off1)
      Then("the exam is in Cancelled state")
      res.status shouldBe StatusCodes.NoContent
      val ex = get("exam", off1).to[List[ExamView]].find(_.id == "e6").get
      ex.state shouldBe "Cancelled"
    }
  }
  */
