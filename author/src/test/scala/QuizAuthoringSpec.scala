package quizzly.author

import org.scalatest.*

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.*
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.client.RequestBuilding.*

import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Await, Future}
import scala.concurrent.duration.*

object QuizAuthoringSpec:
  val config = ConfigFactory.parseString("""
    akka {
      actor {
        provider = "cluster"
        serialization-bindings {
          "quizzly.author.CborSerializable" = jackson-cbor
        }
      }
      persistence {
        journal.plugin = "jdbc-journal"
        auto-start-journals = ["jdbc-journal"]
        snapshot-store.plugin = "jdbc-snapshot-store"
        auto-start-snapshot-stores = ["jdbc-snapshot-store"]
      }
      projection {
        jdbc.blocking-jdbc-dispatcher.thread-pool-executor.fixed-pool-size = 10
        jdbc.dialect = h2-dialect
      }
    }
    jdbc-connection-settings {
      connection-pool {
        timeout = 250ms
        max-pool-size = ${akka.projection.jdbc.blocking-jdbc-dispatcher.thread-pool-executor.fixed-pool-size}
      }
      driver = "org.h2.Driver"
      user = "sa"
      password = "sa"
      url = "jdbc:h2:mem:app"
    }
    akka-persistence-jdbc {
      shared-databases {
        default {
          profile = "slick.jdbc.H2Profile$"
          db {
            host = "localhost"
            url = ${jdbc-connection-settings.url}
            user = ${jdbc-connection-settings.user}
            password = ${jdbc-connection-settings.password}
            driver = ${jdbc-connection-settings.driver}
            numThreads = 5
            maxConnections = 5
            minConnections = 1
          }
        }
      }
    }
    jdbc-journal {
      use-shared-db = "default"
    }
    jdbc-snapshot-store {
      use-shared-db = "default"
    }
    jdbc-read-journal {
      use-shared-db = "default"
    }
    author {
      minAuthors = 2
      minInspectors = 2
      minTrialLength = 5
      minTitleLength = 5
      inactivityMinutes = 10
    }
    frontend-http-port = 9099
    """)
    .resolve

class QuizAuthoringSpec extends featurespec.AnyFeatureSpec, GivenWhenThen, 
    BeforeAndAfterAll, concurrent.Eventually, JsonFormats, matchers.should.Matchers:

  val appSystem = ActorSystem(Behaviors.empty, "app", QuizAuthoringSpec.config)
  val clnSystem = ActorSystem(Behaviors.empty, "cln")

  given ActorSystem[?] = clnSystem
  given ExecutionContext = clnSystem.executionContext

  import testdata.*

  val auth: Auth = new:
    val all = Map(
      curator.id -> curator,
      author1.id -> author1,
      author2.id -> author2,
      author3.id -> author3,
      inspector1.id -> inspector1,
      inspector2.id -> inspector2,
      inspector3.id -> inspector3
    )
    def authenticate(request: HttpRequest) = Future(all(request.getHeader("p").get.value))
    def getPersons = Future(all.values.toSet)
    def getPersons(ids: Set[PersonID]) = Future(all.filter((k, v) => ids(k)).values.toSet)
    def getPerson(id: PersonID) = Future(all.get(id))

  Main(appSystem, auth)

  val http = Http()(using clnSystem)

  override def afterAll() =
    clnSystem.terminate()
    appSystem.terminate()

  info("As an Official")
  info("I want to manage a Quiz")

  Feature("Quiz creation") {

    Scenario("Quiz created") {
      Given("unique identifier specified")
      And("title and intro specified")
      And("Authors and Inspectors specified")
      val req = CreateQuiz(
        id = "ABC-1",
        title = "Quiz ABC",
        intro = "some intro on ABC",
        recommendedLength = 10,
        authors = Set(author1.id, author2.id),
        inspectors = Set(inspector1.id, inspector2.id)
      )
      
      When("'create quiz' request is sent")
      val res = http.singleRequest(Post("http://127.0.0.1:9099/v1/quiz", req) ~> addHeader("p", curator.id))
      
      Then("new Quiz created")
      val details = Await.result(res.flatMap(Unmarshal(_).to[Quiz.CreateDetails]), 1.second)
      details shouldBe Quiz.CreateDetails(
        Set(author1, author2), Set(inspector1, inspector2)
      )
      
      And("I am a Curator")
      And("new Quiz is in Composing state")
      val listed = Await.result(http.singleRequest(Get("http://localhost:9099/v1/quiz") ~> addHeader("p", curator.id))
            .flatMap(Unmarshal(_).to[List[QuizListed]]), 1.second).find(_.id == "ABC-1").get
      listed shouldBe QuizListed(
        id = "ABC-1",
        title = "Quiz ABC",
        state = Quiz.State.COMPOSING,
        obsolete = false,
        curator = curator,
        authors = Set(author1, author2),
        inspectors = Set(inspector1, inspector2)
      )
    }

    Scenario("Quiz not created 1") {
      Given("not unique identifier specified")
      And("title and intro specified")
      And("Authors and Inspectors specified")
      When("'create quiz' request is sent")
      Then("Quiz is not created")
      And("'quiz already exists' message is displayed")
    }

    Scenario("Quiz not created 2") {
      Given("unique identifier specified")
      And("title and intro specified")
      And("Authors and Inspectors not specified")
      When("'create quiz' request is sent")
      Then("Quiz is not created")
      And("'not enough authors or inspectors' message is displayed")
    }

    Scenario("main Quiz attributes changed") {
      Given("a Quiz in Composing state")
      And("I am a Curator")
      And("modified title, intro, recommended length")
      When("'save' request is performed")
      Then("new title, recommended length and intro saved")
    }

    Scenario("set a Quiz Obsolete")(pending)

  }

  info("")

  info("As an Author")
  info("I want to modify a Quiz")

  Feature("Quiz modification")(pending)

  info("")

  info("As an Inspector")
  info("I want to assess a Quiz")

  Feature("Quiz inspection")(pending)
