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

  val authorsIds12 = Set(author1.id, author2.id)
  val inspectorsIds12 = Set(inspector1.id, inspector2.id)

  def create(id: QuizID) =
    val req = CreateQuiz(id, s"Quiz $id", s"some intro for $id", 10, authorsIds12, inspectorsIds12)
    val res = post("quiz", curator, req)
    res.status shouldBe StatusCodes.OK
    res.to[Quiz.CreateDetails] shouldBe
      Quiz.CreateDetails(Set(author1, author2), Set(inspector1, inspector2))

  info("Officials author and manage quizzes")

  Feature("officials list") {

    Scenario("getting officials list") {
      When("staff list is requested by any official")
      val res = get("staff", author3)
      Then("full list of officials is returned")
      res.status shouldBe StatusCodes.OK
      res.to[List[Person]] should contain allOf
        (curator, author1, author2, author3, inspector1, inspector2, inspector3)
    }

    Scenario("access denied for not officials") {
      When("staff list is requested by not official")
      val res = get("staff", Person("not exist", ""))
      Then("access denied")
      res.status shouldBe StatusCodes.Unauthorized
    }

  }

  Feature("quiz list") {
    Scenario("getting quiz list") {
      Given("a quiz existing")
      create("First")
      When("quiz list is requested by any official")
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
            Quiz.State.Composing
          )
      }
    }
    Scenario("access denied for not officials") {
      When("quiz list is requeested by not official")
      val res = get("quiz", Person("notexist", ""))
      Then("request rejected")
      res.status shouldBe StatusCodes.Unauthorized
    }
  }

  Feature("quiz creation") {

    Scenario("quiz created") {
      Given("unique identifier specified")
      And("title and intro specified")
      And("authors and inspectors specified")
      val req = CreateQuiz(
        id = "ABC-1",
        title = "Quiz ABC",
        intro = "some intro on ABC",
        recommendedLength = 10,
        authors = authorsIds12,
        inspectors = inspectorsIds12
      )

      When("curator requested quiz creation")
      val res = post("quiz", curator, req)

      Then("new quiz created")
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
              state = Quiz.State.Composing,
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
      patch("quiz/D/ready", author1)
      patch("quiz/D/ready", author2)
      patch("quiz/D/resolve", inspector1)
      patch("quiz/D/resolve", inspector2)
      val full = get("quiz/D", curator).to[FullQuiz]
      full.state shouldBe Quiz.State.Released
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

  Feature("Quiz modification") {

    Scenario("main Quiz attributes changed") {
      Given("a Quiz in Composing state")
      create("B")
      val full = get("quiz/B", curator).to[FullQuiz]
      full.id shouldBe "B"
      full.state shouldBe Quiz.State.Composing
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
      And("'not author' is displayed")
      res.to[Error] shouldBe Quiz.notAuthor.error()
    }

    Scenario("update rejection 2") {
      Given("a quiz in Composing state")
      create("F")
      And("modified attributes with short title")
      val req = UpdateQuiz("xx", "", 80)
      When("'save' request is done by author")
      val res = put("quiz/F", author1, req)
      Then("operation rejected")
      res.status shouldBe StatusCodes.UnprocessableEntity
      And("'too short title' is displayed")
      res.to[Error] shouldBe Quiz.tooShortTitle.error()
    }

    Scenario("section creation") {
      Given("a quiz in Composing state")
      create("G")
      When("'add section' request is done")
      val res = post("quiz/G", author1, CreateSection("section 1"))
      Then("new section is added")
      res.status shouldBe StatusCodes.OK
      res.to[String] shouldBe "G-1"
      And("I am an owner")
      val ownres = patch("quiz/G?sc=G-1", author1)
      ownres.status shouldBe StatusCodes.UnprocessableEntity
      ownres.to[Error] shouldBe SectionEdit.alreadyOwned.error()
      And("another author cannot own this section")
      val ownres2 = patch("quiz/G?sc=G-1", author2)
      ownres2.status shouldBe StatusCodes.UnprocessableEntity
      ownres2.to[Error] shouldBe SectionEdit.notOwner.error()
      And("another author cannot edit this section")
      val upd = put("section/G-1", author2, UpdateSection("", ""))
      upd.status shouldBe StatusCodes.UnprocessableEntity
      upd.to[Error] shouldBe SectionEdit.notOwner.error()
      And("curator cannot remove this section")
      val rem = delete("section/G-1?qid=G", curator)
      rem.status shouldBe StatusCodes.UnprocessableEntity
      rem.to[Error] shouldBe Quiz.notAuthor.error()
      And("another author cannot remove this section")
      val rem2 = delete("section/G-1?qid=G", author2)
      rem2.status shouldBe StatusCodes.UnprocessableEntity
      rem2.to[Error] shouldBe SectionEdit.alreadyOwned.error()
    }

    Scenario("section update") {
      Given("a quiz in Composing state")
      create("H")
      And("a section added and owned")
      post("quiz/H", author1, CreateSection("section 1"))
      When("'update section' request is made")
      val upd = put("section/H-1", author1, UpdateSection("section 1 plus", "section 1 intro"))
      upd.status shouldBe StatusCodes.NoContent
      And("section discharged")
      val dis = get("section/H-1", author1)
      dis.status shouldBe StatusCodes.NoContent
      Then("the section updated")
      val full = get("quiz/H", author1)
      val sect = full.to[FullQuiz].sections.head
      sect.title shouldBe "section 1 plus"
      sect.intro shouldBe "section 1 intro"
    }

    Scenario("section removal") {
      Given("a quiz in Composing state")
      create("I")
      And("a section added")
      post("quiz/I", author1, CreateSection("section 1"))
      And("the section discharged")
      get("section/I-1", author1)
      val full0 = get("quiz/I", author1)
      full0.to[FullQuiz].sections.exists(_.sc == "I-1") shouldBe true
      When("another author requests section removal")
      val del = delete("section/I-1?qid=I", author2)
      Then("the section is removed")
      del.status shouldBe StatusCodes.NoContent
      val full = get("quiz/I", author1)
      full.to[FullQuiz].sections.find(_.sc == "I-1") shouldBe None
    }

    Scenario("item management") {
      Given("a quiz in Composing state")
      create("J")
      And("a section added and owned")
      post("quiz/J", author1, CreateSection("section 1"))
      And("an item added to the section")
      val add = patch("section/J-1/items", author1)
      add.status shouldBe StatusCodes.OK
      add.to[String] shouldBe "1"
      When("'save item' request is made")
      val save = put("section/J-1/items", author1, item.copy(sc = "1"))
      Then("the item is saved")
      save.status shouldBe StatusCodes.NoContent
      get("section/J-1", author1)
      val full = get("quiz/J", author1).to[FullQuiz]
      full.sections.find(_.sc == "J-1").get.items.exists(_.sc == "1") shouldBe true

      When("another author owned the section")
      val own = patch("quiz/J?sc=J-1", author2)
      own.status shouldBe StatusCodes.NoContent
      And("another item added and save request is made")
      val add2 = patch("section/J-1/items", author2)
      add2.status shouldBe StatusCodes.OK
      add2.to[String] shouldBe "2"
      val save2 = put("section/J-1/items", author2, item.copy(sc = "2"))
      Then("new item is saved")
      save2.status shouldBe StatusCodes.NoContent
      get("section/J-1", author2)
      val full2 = get("quiz/J", inspector1)
      full2.status shouldBe StatusCodes.OK
      full2.to[FullQuiz].sections.find(_.sc == "J-1").get.items.map(_.sc) should contain inOrder
        ("1", "2")

      When("first author owned the section")
      patch("quiz/J?sc=J-1", author1)
      And("moved an item up")
      val up = patch("section/J-1/items/2?up=true", author1)
      Then("item changed position")
      up.status shouldBe StatusCodes.OK
      up.to[StrList].list should contain inOrder ("2", "1")
      get("section/J-1", author1)
      val full4 = get("quiz/J", author1).to[FullQuiz]
      full4.sections.find(_.sc == "J-1").get.items.map(_.sc) should contain inOrder ("2", "1")

      patch("quiz/J?sc=J-1", author1)
      When("requested item removal")
      val rem = delete("section/J-1/items/1", author1)
      Then("the item is removed")
      rem.status shouldBe StatusCodes.NoContent
      get("section/J-1", author1)
      val full3 = get("quiz/J", curator).to[FullQuiz]
      full3.sections.find(_.sc == "J-1").get.items.map(_.sc).toSet shouldBe Set("2")
    }

    Scenario("setting readiness sings") {
      Given("a quiz in Composing state")
      create("K")
      When("author sets readiness sign")
      val sign = patch("quiz/K/ready", author1)
      sign.status shouldBe StatusCodes.NoContent
      Then("quiz has sign of this author")
      val full1 = get("quiz/K", author1).to[FullQuiz]
      full1.readinessSigns.toSet shouldBe Set(author1)
      When("author unsets readiness sign")
      val unsign = delete("quiz/K/ready", author1)
      unsign.status shouldBe StatusCodes.NoContent
      Then("quiz has no readiness signs")
      val full2 = get("quiz/K", author1).to[FullQuiz]
      full2.readinessSigns shouldBe empty
      When("both authors set readiness signs")
      patch("quiz/K/ready", author1)
      patch("quiz/K/ready", author2)
      Then("quiz is fully signed and gone for Review")
      val full3 = get("quiz/K", curator).to[FullQuiz]
      full3.state shouldBe Quiz.State.Review
    }

  }

  info("")

  Feature("Quiz inspection") {
    
    Scenario("approval and disapproval") {
      Given("a quiz in Review state")
      create("L")
      patch("quiz/L/ready", author1)
      patch("quiz/L/ready", author2)
      When("inspector approves the quiz")
      val approve = patch("quiz/L/resolve", inspector1)
      Then("quiz has approval of this inspector")
      approve.status shouldBe StatusCodes.NoContent
      val full1 = get("quiz/L", curator).to[FullQuiz]
      full1.approvals.toSet shouldBe Set(inspector1)
      full1.disapprovals shouldBe empty

      When("inspector disapproves the quiz")
      val disapprove = delete("quiz/L/resolve", inspector1)
      Then("quiz has no approvals and disapprovals")
      disapprove.status shouldBe StatusCodes.NoContent
      val full2 = get("quiz/L", curator).to[FullQuiz]
      full2.approvals shouldBe empty
      full2.disapprovals.toSet shouldBe Set(inspector1)
    }

    Scenario("disapproval to Composing") {
      Given("a quiz in Review state")
      create("M")
      patch("quiz/M/ready", author1)
      patch("quiz/M/ready", author2)
      When("both inspectors disapprove the quiz")
      delete("quiz/M/resolve", inspector1)
      delete("quiz/M/resolve", inspector2)
      Then("the quiz returns to Composing state")
      val full = get("quiz/M", curator).to[FullQuiz]
      full.state shouldBe Quiz.State.Composing
      full.approvals shouldBe empty
      full.disapprovals shouldBe empty
      full.readinessSigns shouldBe empty
    }

    Scenario("approval to Released") {
      Given("a quiz in Review state")
      create("O")
      patch("quiz/O/ready", author1)
      patch("quiz/O/ready", author2)
      When("both inspectors approve the quiz")
      patch("quiz/O/resolve", inspector1)
      patch("quiz/O/resolve", inspector2)
      Then("the quiz is released")
      val full = get("quiz/O", curator).to[FullQuiz]
      full.state shouldBe Quiz.State.Released
      full.approvals shouldBe empty
      full.disapprovals shouldBe empty
      full.readinessSigns shouldBe empty
    }

  }
