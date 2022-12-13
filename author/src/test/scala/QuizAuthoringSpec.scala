package quizzly.author

import org.scalatest.*

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.*
import akka.http.scaladsl.unmarshalling.*
import akka.http.scaladsl.marshalling.*
import akka.http.scaladsl.client.RequestBuilding.*

import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Await, Future}
import scala.concurrent.duration.*

object QuizAuthoringSpec:
  def config(nodePort: Int, httpPort: Int) =
    ConfigFactory
      .parseString(s"""
    akka {
      actor {
        provider = "cluster"
        serialization-bindings {
          "quizzly.author.CborSerializable" = jackson-cbor
        }
      }
      cluster {
        roles = ["author"]
        seed-nodes = [
          "akka://app@localhost:$nodePort"
        ]
        sharding {
          number-of-shards = 100
        }
        downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
      }
      remote.artery {
        canonical {
          hostname = "localhost"
          port = "$nodePort"
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
        max-pool-size = $${akka.projection.jdbc.blocking-jdbc-dispatcher.thread-pool-executor.fixed-pool-size}
      }
      driver = "org.h2.Driver"
      user = "sa"
      password = "sa"
      url = "jdbc:h2:mem:app"
      migrations-table = "schemahistory"
      migrations-locations = ["classpath:db"]
      migration = on
    }
    akka-persistence-jdbc {
      shared-databases {
        default {
          profile = "slick.jdbc.H2Profile$$"
          db {
            host = "localhost"
            url = $${jdbc-connection-settings.url}
            user = $${jdbc-connection-settings.user}
            password = $${jdbc-connection-settings.password}
            driver = $${jdbc-connection-settings.driver}
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
    frontend.http {
      host = "localhost"
      port = $httpPort
    }
    """)
      .resolve

class QuizAuthoringSpec
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

  val appSystem = ActorSystem(Behaviors.empty, "app", QuizAuthoringSpec.config(nodePort, httpPort))
  val clnSystem = ActorSystem(
    Behaviors.empty,
    "cln",
    ConfigFactory.parseString("""{akka.actor.provider = "local"}""")
  )

  given ActorSystem[?] = clnSystem
  given ExecutionContext = clnSystem.executionContext

  import testdata.*

  val auth: Auth =
    new:
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

  extension (response: HttpResponse)
    def to[C](using FromResponseUnmarshaller[C]) = Await.result(Unmarshal(response).to[C], 1.second)
  extension (path: String)
    def fullUrl = s"http://localhost:$httpPort/v1/$path"
  def request(req: HttpRequest, as: Person) = Await
    .result(http.singleRequest(req ~> addHeader("p", as.id)), 1.second)
  def post[C](path: String, as: Person, content: C)(using ToEntityMarshaller[C]) = request(
    Post(path.fullUrl, content),
    as
  )
  def put[C](path: String, as: Person, content: C)(using ToEntityMarshaller[C]) = request(
    Put(path.fullUrl, content),
    as
  )
  def get(path: String, as: Person) = request(Get(path.fullUrl), as)
  def delete(path: String, as: Person) = request(Delete(path.fullUrl), as)
  def head(path: String, as: Person) = request(Head(path.fullUrl), as)

  val authorsIds12 = Set(author1.id, author2.id)
  val inspectorsIds12 = Set(inspector1.id, inspector2.id)

  def create(id: QuizID) =
    val req = CreateQuiz(id, s"Quiz $id", s"some intro for $id", 10, authorsIds12, inspectorsIds12)
    val res = post("quiz", curator, req)
    res.status shouldBe StatusCodes.OK
    res.to[Quiz.CreateDetails] shouldBe
      Quiz.CreateDetails(Set(author1, author2), Set(inspector1, inspector2))

  info("As an Official")
  info("I want to manage a Quiz")

  Feature("officials list") {

    Scenario("getting officials list") {
      When("'get staff' request is made")
      val res = get("staff", author3)
      Then("full list of officials is returned")
      res.status shouldBe StatusCodes.OK
      res.to[List[Person]] should contain allOf
        (curator, author1, author2, author3, inspector1, inspector2, inspector3)
    }

    Scenario("access denied for not officials") {
      When("'get staff' request is done by not official")
      val res = get("staff", Person("not exist", ""))
      Then("access denied")
      res.status shouldBe StatusCodes.Unauthorized
    }

  }

  Feature("quiz list") {
    Scenario("getting quiz list") {
      Given("a quiz existing")
      create("First")
      When("'get quiz list' request is done by official")
      Then("quiz list is returned")
      eventually {
        get("quiz", curator).to[List[QuizListed]].find(_.id == "First").get shouldBe
          QuizListed(
            "First",
            "Quiz First",
            false,
            curator,
            Set(author1, author2),
            Set(inspector1, inspector2),
            Quiz.State.COMPOSING
          )
      }
    }
    Scenario("access denied for not officials") {
      When("'get quiz list' request is done by not official")
      val res = get("quiz", Person("notexist", ""))
      Then("request rejected")
      res.status shouldBe StatusCodes.Unauthorized
    }
  }

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
        authors = authorsIds12,
        inspectors = inspectorsIds12
      )

      When("'create quiz' request is sent")
      val res = post("quiz", curator, req)

      Then("new Quiz created")
      res.status shouldBe StatusCodes.OK
      res.to[Quiz.CreateDetails] shouldBe
        Quiz.CreateDetails(Set(author1, author2), Set(inspector1, inspector2))

      And("I am a Curator")
      And("new Quiz is in Composing state")
      eventually {
        val listed = get("quiz", curator).to[List[QuizListed]].find(_.id == "ABC-1")
        listed shouldBe
          Some(
            QuizListed(
              id = "ABC-1",
              title = "Quiz ABC",
              state = Quiz.State.COMPOSING,
              obsolete = false,
              curator = curator,
              authors = Set(author1, author2),
              inspectors = Set(inspector1, inspector2)
            )
          )
      }
    }

    Scenario("Quiz not created 1") {
      create("A")
      Given("not unique identifier specified")
      And("title and intro specified")
      And("Authors and Inspectors specified")
      val req = CreateQuiz("A", "title", "", 10, authorsIds12, inspectorsIds12)
      When("'create quiz' request is sent")
      val res = post("quiz", curator, req)
      Then("Quiz is not created")
      res.status shouldBe StatusCodes.UnprocessableEntity
      And("'quiz already exists' message is displayed")
      res.to[Error] shouldBe Quiz.quizAlreadyExists.error() + "A"
    }

    Scenario("Quiz not created 2") {
      Given("unique identifier specified")
      And("title and intro specified")
      And("Authors not specified")
      val req = CreateQuiz("B", "title", "", 10, Set.empty, inspectorsIds12)
      When("'create quiz' request is sent")
      val res = post("quiz", curator, req)
      Then("Quiz is not created")
      res.status shouldBe StatusCodes.UnprocessableEntity
      And("'not enough authors' message is displayed")
      res.to[Error] shouldBe Quiz.notEnoughAuthors.error()
    }

    Scenario("set a Quiz Obsolete") {
      Given("a Quiz in Released state")
      create("D")
      head("quiz/D/ready", author1)
      head("quiz/D/ready", author2)
      head("quiz/D/resolve", inspector1)
      head("quiz/D/resolve", inspector2)
      val full = get("quiz/D", curator).to[FullQuiz]
      full.state shouldBe Quiz.State.RELEASED
      And("I am a Curator")
      When("'set obsolete' request is made")
      delete("quiz/D", curator).status shouldBe StatusCodes.NoContent
      Then("the quiz is obsolete")
      val obsolete = get("quiz/D", curator).to[FullQuiz]
      obsolete.obsolete shouldBe true
    }

    Scenario("cannot set a quiz obsolete") {
      Given("a Quiz in Composing state")
      create("C")
      And("I am a Curator")
      When("'set obsolete' request is made")
      val res = delete("quiz/C", curator)
      Then("operation is rejected")
      res.status shouldBe StatusCodes.UnprocessableEntity
      res.to[Error] shouldBe Quiz.isComposing.error()
    }

  }

  info("")

  info("As an Author")
  info("I want to modify a Quiz")

  Feature("Quiz modification") {

    Scenario("main Quiz attributes changed") {
      Given("a Quiz in Composing state")
      create("B")
      val full = get("quiz/B", curator).to[FullQuiz]
      full.id shouldBe "B"
      full.state shouldBe Quiz.State.COMPOSING
      full.curator shouldBe curator
      And("I am a author")
      And("modified title, intro, recommended length")
      val req = UpdateQuiz("title plus", "intro plus", 77)
      When("'save' request is performed")
      val res = put("quiz/B", author1, req)
      res.status shouldBe StatusCodes.NoContent
      Then("new title, recommended length and intro saved")
      val saved = get("quiz/B", curator).to[FullQuiz]
      saved.title shouldBe "title plus"
      saved.intro shouldBe "intro plus"
      saved.recommendedLength shouldBe 77
      eventually {
        val listed = get("quiz", curator).to[List[QuizListed]].find(_.id == "B").get
        listed.title shouldBe "title plus"
      }
    }

    Scenario("update rejection 1") {
      Given("a quiz in Composing state")
      create("E")
      And("modified attributes")
      val req = UpdateQuiz("title plus", "", 100)
      When("'save' request is done by not author")
      val res = put("quiz/E", author3, req)
      Then("operation rejected")
      res.status shouldBe StatusCodes.UnprocessableEntity
      res.to[Error] shouldBe Quiz.notAuthor.error()
    }

  }

  info("")

  info("As an Inspector")
  info("I want to assess a Quiz")

  Feature("Quiz inspection")(pending)
