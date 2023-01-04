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

object ExamManagementSpec:
  def config(nodePort: Int, httpPort: Int) =
    ConfigFactory
      .parseString(s"""
    akka.cluster.seed-nodes = ["akka://app@localhost:$nodePort"]
    akka.remote.artery.canonical.port = "$nodePort"
    jdbc-connection-settings.url = "jdbc:h2:mem:app"
    frontend.http.port = $httpPort
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

  Feature("Create an Exam") {

    Scenario("Exam is created if all input is correct") {
      Given("specified unique Exam identifier")
      And("specified Quiz")
      And("specified Exam Period")
      When("create request is performed")
      Then("new Exam is created")
      And("its state is Pending")
      And("I am a Host of this Exam")
    }

    Scenario("Exam is not created if identifier is not unique") {
      Given("specified identifier that already exists")
      And("specified Quiz")
      And("specified Exam Period")
      When("create request is performed")
      Then("new Exam is not created")
      And("response contains detailed reason")
    }
  }

  Feature("Include/exclude a Testee") {

    Scenario("Testee added") {
      Given("existing Exam")
      And("it is in Pending state")
      And("I am a Host")
      And("specified a user as a new Testee")
      When("'add testee' request is performed")
      Then("specified user is on Exam list as a Testee")
    }

    Scenario("Testee removed") {
      Given("existing Exam")
      And("it is in Pending state")
      And("I am a Host")
      And("a specific Testee is on list")
      When("'remove testee' request is performed")
      Then("specified Testee is no longer on Exam list")
    }
  }

  Feature("Modify Trial Length") {
    Scenario("Trial Length is changed") {
      Given("existing Exam")
      And("it is in Pending state")
      And("I am a Host")
      And("specified new Trial Length")
      When("'change trial length' request is performed")
      Then("specified Trial Length is set on the Exam")
    }
  }

  Feature("Cancel Exam") {
    Scenario("Exam is cancelled") {
      Given("existing Exam")
      And("it is in Pending or Upcoming state")
      And("I am a Host")
      When("'cancel exam' request is performed")
      Then("the exam is in Cancelled state")
    }
  }
