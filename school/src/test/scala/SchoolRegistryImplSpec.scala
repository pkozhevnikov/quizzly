package quizzly.school

import scala.concurrent.*
import scala.concurrent.duration.*
import scala.util.*

import akka.serialization.jackson.JacksonObjectMapperProvider
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import org.scalatest.*
import scalikejdbc.*

import java.time.*

class SchoolRegistryImplSpec
    extends wordspec.AnyWordSpec,
      BeforeAndAfterEach,
      BeforeAndAfterAll,
      matchers.should.Matchers:

  val testKit = ActorTestKit("regtest")

  given ExecutionContext = testKit.system.executionContext

  override def beforeAll() = super.beforeAll()

  override def afterAll() =
    super.afterAll()
    testKit.shutdownTestKit()

  var factProbe: TestProbe[QuizFact.Command] = null
  var examProbe: TestProbe[Exam.Command] = null

  override def beforeEach() =
    super.beforeEach()
    factProbe = testKit.createTestProbe()
    examProbe = testKit.createTestProbe()

  override def afterEach() =
    super.afterEach()
    factProbe.stop()
    examProbe.stop()

  "SchoolRegistry" should {

    "register quiz" in {
      var reqId = ""
      val fact =
        (id: String) =>
          reqId = id
          factProbe.ref
      val registry = SchoolRegistryImpl(fact, _ => examProbe.ref)
      val res = registry.registerQuiz(grpc.RegisterQuizRequest("q1", "q1 title", 45))
      Await.result(res, 1.second) shouldBe grpc.RegisterQuizResponse.of()
      reqId shouldBe "q1"
      factProbe.expectMessage(QuizFact.Init("q1 title", false, 45))
    }

    "register trial" in {
      var reqId = ""
      val exam =
        (id: String) =>
          reqId = id
          examProbe.ref
      val registry = SchoolRegistryImpl(_ => factProbe.ref, exam)
      val start = Instant.parse("2023-01-30T10:00:00Z")
      val end = Instant.parse("2023-01-30T10:10:00Z")
      val res = registry.registerTrialResults(
        grpc.RegisterTrialResultsRequest(
          "e1",
          "testee1",
          "t1",
          start.getEpochSecond,
          end.getEpochSecond,
          Seq(grpc.Solution("s1", "i1", Seq("a1", "a2")))
        )
      )
      Await.result(res, 1.second) shouldBe grpc.RegisterTrialResultsResponse.of()
      reqId shouldBe "e1"
      examProbe.expectMessage(
        Exam.RegisterTrial(
          TrialOutcome("testee1", "t1", start, end, List(Solution("s1", "i1", List("a1", "a2"))))
        )
      )
    }

    "set quiz obsolete" in {
      var reqId = ""
      val fact =
        (id: String) =>
          reqId = id
          factProbe.ref
      val registry = SchoolRegistryImpl(fact, _ => examProbe.ref)
      val res = registry.setQuizObsolete(grpc.SetObsoleteRequest("q1"))
      Await.result(res, 1.second) shouldBe grpc.SetObsoleteResponse.of()
      reqId shouldBe "q1"
      factProbe.expectMessage(QuizFact.SetObsolete)
    }
    
  }
