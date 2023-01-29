package quizzly.trial

import akka.actor.typed.ActorRef
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.persistence.testkit.scaladsl.{EventSourcedBehaviorTestKit as TestKit}
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit.*

import java.time.*

import org.scalatest.*

import com.typesafe.config.*

object ExamEntitySpec:
  val config: Config = ConfigFactory
    .parseString("""
      akka.actor {
        serialization-bindings {
          "quizzly.trial.CborSerializable" = jackson-cbor
        }
      }
      """)
    .withFallback(TestKit.config)

class ExamEntitySpec
    extends wordspec.AnyWordSpec,
      matchers.should.Matchers,
      BeforeAndAfterEach,
      BeforeAndAfterAll:

  val testKit = ActorTestKit(ExamEntitySpec.config)

  val id = "exam-1"

  import ExamEntity.*

  var nowTime = Instant.parse("2023-01-30T10:00:00Z")
  given (() => Instant) = () => nowTime

  private val kit = TestKit[Command, Event, Option[Exam]](testKit.system, ExamEntity(id))

  override protected def beforeEach(): Unit =
    super.beforeEach()
    kit.clear()

  override protected def afterAll() = testKit.shutdownTestKit()

  val person1 = Person("pers1", "pers1 name")
  val person2 = Person("pers2", "pers2 name")
  val person3 = Person("pers3", "pers3 name")
  val persons = Set(person1, person2, person3)

  import Resp.*

  "ExamEntity" when {

    val period = ExamPeriod(
      Instant.parse("2023-01-29T10:00:00Z"),
      Instant.parse("2023-01-30T10:00:00Z")
    )

    "not registered yet" should {

      "reject any command except register" in {
        val result1 = kit.runCommand(RegisterTestee("", Person("", ""), _))
        result1.hasNoEvents shouldBe true
        result1.reply shouldBe Bad(Trial.examNotFound.error())
        val result2 = kit.runCommand(Unregister)
        result2.hasNoEvents shouldBe true
        result2.state shouldBe None
      }

      "register exam" in {
        val result = kit.runCommand(Register("q1", period, 55, persons))
        result.event shouldBe Registered("q1", period, 55, persons)
        result.state shouldBe
          Some(
            Exam(
              "q1",
              period,
              (Instant.parse("2023-01-29T10:00:00Z"), Instant.parse("2023-01-30T09:05:00Z")),
              55,
              persons.map((_, None)).toMap
            )
          )
      }

    }

    "registered" should {

      "return exam info" in {
        kit.runCommand(Register("q1", period, 55, persons))
        val result = kit.runCommand(GetInfo(_))
        result.hasNoEvents shouldBe true
        result.reply shouldBe Good(ExamAttrs("exam-1", "q1", period.start, period.end, 55))
      }

      "reject register testee if testee is not eligible" in {
        kit.runCommand(Register("q1", period, 55, persons))
        val result = kit.runCommand(RegisterTestee("t1", Person("nottestee", ""), _))
        result.hasNoEvents shouldBe true
        result.reply shouldBe Bad(Trial.notTestee.error())
      }

      "reject register testee if time over" in {
        kit.runCommand(Register("q1", period, 55, persons))
        nowTime = period.end
        val result = kit.runCommand(RegisterTestee("t1", person1, _))
        result.hasNoEvents shouldBe true
        result.reply shouldBe Bad(Trial.examEnded.error())
        nowTime = period.start
      }

      "reject register command" in {
        kit.runCommand(Register("q1", period, 55, persons))
        val result = kit.runCommand(Register("q1", period, 55, persons))
        result.hasNoEvents shouldBe true
      }

      "reject register testee if already registered" in {
        kit.runCommand(Register("q1", period, 55, persons))
        kit.runCommand(RegisterTestee("t1", person2, _))
        val result = kit.runCommand(RegisterTestee("t1", person2, _))
        result.hasNoEvents shouldBe true
        result.reply shouldBe Bad(Trial.trialAlreadyStarted.error())
      }

      "register testee" in {
        kit.runCommand(Register("q1", period, 55, persons))
        val result = kit.runCommand(RegisterTestee("t3", person3, _))
        result.event shouldBe TesteeRegistered("t3", person3)
        result.reply shouldBe
          Good(
            ExamAttrs(
              "exam-1",
              "q1",
              Instant.parse("2023-01-29T10:00:00Z"),
              Instant.parse("2023-01-30T09:05:00Z"),
              55
            )
          )
        result.state shouldBe
          Some(
            Exam(
              "q1",
              period,
              (Instant.parse("2023-01-29T10:00:00Z"), Instant.parse("2023-01-30T09:05:00Z")),
              55,
              Map(person1 -> None, person2 -> None, person3 -> Some("t3"))
            )
          )
      }

      "reject unregister if exam is still in progress" in {
        kit.runCommand(Register("q1", period, 55, persons))
        nowTime = Instant.parse("2023-01-29T15:00:00Z")
        val result = kit.runCommand(Unregister)
        result.hasNoEvents shouldBe true
      }

      "unregister" in {
        kit.runCommand(Register("q1", period, 55, persons))
        nowTime = Instant.parse("2023-01-30T10:10:00Z")
        val result = kit.runCommand(Unregister)
        result.event shouldBe Unregistered
        result.state shouldBe None
      }

    }

  }
