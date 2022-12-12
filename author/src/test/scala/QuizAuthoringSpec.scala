package quizzly.author

import org.scalatest.*

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.*
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.client.RequestBuilding.*

import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext

object QuizAuthoringSpec:
  val config = ConfigFactory.parseString("""
    """)
    .resolve

class QuizAuthoringSpec extends featurespec.AnyFeatureSpec, GivenWhenThen, 
    BeforeAndAfterAll, concurrent.Eventually, JsonFormats:

  val appSystem = ActorSystem(Behaviors.empty, "app", QuizAuthoringSpec.config)
  val clnSystem = ActorSystem(Behaviors.empty, "cln")

  given ActorSystem[?] = clnSystem
  given ExecutionContext = clnSystem.executionContext

  Main(appSystem)

  val http = Http()(using clnSystem)

  import testdata.*

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
      val post = Post("/v1/quiz", req) ~> addHeader("p", curator.id)
      val res = http.singleRequest(Post("/v1/quiz", post)).flatMap(Unmarshal(_).to[Quiz.CreateDetails])
      Then("new Quiz created")
      eventually {
        res shouldBe Quiz.CreateDetails(
          Set(author1, author2), Set(inspector1, inspector2)
        )
      }
      And("I am a Curator")
      val listed = http.singleRequest(Get("/v1/quiz") ~> addHeader("p", curator.id))
            .flatMap(Unmarshal(_).to[List[QuizListed]]).find(_.id == "ABC-1").get
      eventually {
        listed.curator shouldBe curator
      }
      And("new Quiz is in Composing state")
      eventually {
        listed.status shouldBe "COMPOSING"
      }
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
