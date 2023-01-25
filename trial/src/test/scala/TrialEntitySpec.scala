package quizzly.trial

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.persistence.testkit.scaladsl.{EventSourcedBehaviorTestKit as TestKit}
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit.*
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.testkit.scaladsl.TestEntityRef

import scala.concurrent.{Future, ExecutionContext}

import java.time.*

import org.scalatest.*

import com.typesafe.config.*

object TrialEntitySpec:
  val config: Config = ConfigFactory
    .parseString("""
      akka.actor {
        serialization-bindings {
          "quizzly.trial.CborSerializable" = jackson-cbor
        }
      }
      """)
    .withFallback(TestKit.config)

class TrialEntitySpec
    extends wordspec.AnyWordSpec,
      matchers.should.Matchers,
      BeforeAndAfterEach,
      BeforeAndAfterAll:

  val testKit = ActorTestKit(TrialEntitySpec.config)

  val id = "trial-1"

  import Trial.*

  given ExecutionContext = testKit.system.executionContext

  var nowTime = Instant.parse("2023-01-30T10:00:00Z")
  given (() => Instant) = () => nowTime

  var exams = Map.empty[ExamID, EntityRef[ExamEntity.Command]]
  def putExam(id: ExamID, beh: Behavior[ExamEntity.Command]) =
    exams = exams + (id -> TestEntityRef(ExamEntity.EntityKey, id, testKit.spawn(beh)))

  override protected def beforeEach(): Unit =
    super.beforeEach()
    kit.clear()

  override protected def afterAll() = testKit.shutdownTestKit()

  val person1 = Person("pers1", "pers1 name")
  val person2 = Person("pers2", "pers2 name")
  val person3 = Person("pers3", "pers3 name")

  val section1 = Section(
    "q1-1",
    "section 1 title",
    "section 1 intro",
    List(Item("i1", "i1 intro", Statement("i1 def", None), List.empty, false, List.empty))
  )

  val quiz1 = Quiz("q1", "q1 title", "q1 intro", List(section1))

  val quizRegistry: QuizRegistry =
    new:
      def get(id: QuizID) =
        id match
          case "q1" =>
            Future.successful(quiz1)
          case _ =>
            Future.failed(java.util.NoSuchElementException(s"not found quiz [$id]"))

  private val kit = TestKit[Command, Event, Option[Trial]](
    testKit.system,
    TrialEntity(id, exams(_), quizRegistry)
  )

  import Resp.*

  "TrialEntity" when {

    "not started yet" should {

      "reject any command except start" ignore {
        val res1 = kit.runCommand(Submit(person1, "", List.empty, _))
        res1.hasNoEvents shouldBe true
        res1.reply shouldBe Bad(trialNotStarted.error())
        val res2 = kit.runCommand(Finalize(person1, _))
        res2.hasNoEvents shouldBe true
        res2.reply shouldBe Bad(trialNotStarted.error())
      }

      "reject start if exam rejected" ignore {
        putExam(
          "e2",
          Behaviors.receiveMessage[ExamEntity.Command] {
            case ExamEntity.RegisterTestee(_, _, replyTo) =>
              replyTo ! Bad(examNotFound.error())
              Behaviors.stopped
            case _ =>
              Behaviors.stopped
          }
        )
        val result = kit.runCommand(Start(person1, "e2", _))
        result.hasNoEvents shouldBe true
        result.reply shouldBe Bad(examNotFound.error())
      }

      "be started" in {
        nowTime = Instant.parse("2023-01-29T10:00:00Z")
        putExam(
          "e1",
          Behaviors.receiveMessage[ExamEntity.Command] {
            case ExamEntity.RegisterTestee(trialId, pers, replyTo) =>
              replyTo !
                Good(
                  ExamEntity.ExamAttrs(
                    "e1",
                    "q1",
                    Instant.parse("2023-01-28T10:00:00Z"),
                    Instant.parse("2023-01-30T10:00:00Z"),
                    55
                  )
                )
              Behaviors.stopped
            case _ =>
              Behaviors.stopped
          }
        )
        val result = kit.runCommand(Start(person1, "e1", _))
        val started = Instant.parse("2023-01-29T10:00:00Z")
        result.event shouldBe Started(person1, "e1", quiz1, 55, started, "q1-1")
        result.state shouldBe
          Some(Trial(person1, "e1", quiz1, 55, started, None, "q1-1", Map.empty))
        result.reply shouldBe
          Good(StartTrialDetails("trial-1", person1, started, 55, quiz1.sections(0).view))
      }

    }

    val started = Instant.parse("2023-01-29T10:00:00Z")
    def start =
      nowTime = Instant.parse("2023-01-29T10:00:00Z")
      putExam(
        "e1",
        Behaviors.receiveMessage[ExamEntity.Command] {
          case ExamEntity.RegisterTestee(trialId, pers, replyTo) =>
            replyTo !
              Good(
                ExamEntity.ExamAttrs(
                  "e1",
                  "q1",
                  Instant.parse("2023-01-28T10:00:00Z"),
                  Instant.parse("2023-01-30T10:00:00Z"),
                  55
                )
              )
            Behaviors.stopped
          case _ =>
            Behaviors.stopped
        }
      )
      val result = kit.runCommand(Start(person1, "e1", _))
      result.event shouldBe Started(person1, "e1", quiz1, 55, started, "q1-1")
      result.state shouldBe Some(Trial(person1, "e1", quiz1, 55, started, None, "q1-1", Map.empty))
      result.reply shouldBe
        Good(StartTrialDetails("trial-1", person1, started, 55, quiz1.sections(0).view))

    "started" should {

      "reject start if trial is already started" ignore {
        start
        val result = kit.runCommand(Start(person1, "e1", _))
        result.hasNoEvents shouldBe true
        result.reply shouldBe Bad(trialAlreadyStarted.error())
      }

      "reject any command if trial is finalized" ignore {
        start
        val nt = Instant.parse("2023-01-29T10:10:00Z")
        nowTime = nt
        kit.runCommand(Finalize(person1, _))
        val result = kit.runCommand(Finalize(person1, _))
        result.hasNoEvents shouldBe true
        result.reply shouldBe Bad(trialFinalized.error())
        val result2 = kit.runCommand(Submit(person1, "", List.empty, _))
        result2.hasNoEvents shouldBe true
        result2.reply shouldBe Bad(trialFinalized.error())
      }

      "reject any command if submitter is not a testee of the trial" ignore {
        start
        val result = kit.runCommand(Submit(person2, "", List.empty, _))
        result.hasNoEvents shouldBe true
        result.reply shouldBe Bad(notTestee.error())
      }

      "reject submit if item already submitted" ignore {
        start
        kit.runCommand(Submit(person1, "i1", List("1", "2"), _))
        val result = kit.runCommand(Submit(person1, "i1", List("1", "2"), _))
        result.hasNoEvents shouldBe true
        result.reply shouldBe Bad(itemAlreadySubmitted.error())
      }

      "reject submit if item not found" ignore {
        start
        val result = kit.runCommand(Submit(person1, "i2", List.empty, _))
        result.hasNoEvents shouldBe true
        result.reply shouldBe Bad(itemNotFound.error())
      }

      "submit an item" ignore {
        start
        val result = kit.runCommand(Submit(person1, "i1", List("1", "2"), _))
        result.event shouldBe Submitted("i1", List("1", "2"))
        result.state shouldBe
          Some(
            Trial(
              person1,
              "e1",
              quiz1,
              55,
              started,
              None,
              "q1-1",
              Map(("q1-1", "i1") -> List("1", "2"))
            )
          )
        result.reply shouldBe Good(Some(quiz1.sections(1).view))
      }

      "finialize" ignore {
        start
        val nt = Instant.parse("2023-01-29T10:10:00Z")
        nowTime = nt
        val result = kit.runCommand(Finalize(person1, _))
        result.reply shouldBe Resp.OK
        result.event shouldBe Finalized(nt)
        result.stateOfType[Option[Trial]].get.finalizedAt shouldBe Some(nt)
      }

    }

  }
