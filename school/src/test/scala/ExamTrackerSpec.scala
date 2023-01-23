package quizzly.school

import akka.actor.typed.*
import akka.actor.testkit.typed.scaladsl.*
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit.*

import scala.concurrent.duration.*

import java.time.*

import org.scalatest.*

import com.typesafe.config.*

object ExamTrackerSpec:
  val config: Config = ConfigFactory
    .parseString("""
      akka.actor {
        serialization-bindings {
          "quizzly.school.CborSerializable" = jackson-cbor
        }
      }
      akka.serialization.jackson {
        jackson-modules += "akka.serialization.jackson.AkkaJacksonModule"
        # AkkaTypedJacksonModule optionally included if akka-actor-typed is in classpath
        jackson-modules += "akka.serialization.jackson.AkkaTypedJacksonModule"
        # AkkaStreamsModule optionally included if akka-streams is in classpath
        jackson-modules += "akka.serialization.jackson.AkkaStreamJacksonModule"
        jackson-modules += "com.fasterxml.jackson.module.paramnames.ParameterNamesModule"
        jackson-modules += "com.fasterxml.jackson.datatype.jdk8.Jdk8Module"
        jackson-modules += "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule"
        jackson-modules += "com.fasterxml.jackson.module.scala.DefaultScalaModule"

        jackson-modules += "com.github.pjfanning.enum.EnumModule"
      }
      """)
    .withFallback(ManualTime.config)
    .withFallback(EventSourcedBehaviorTestKit.config)
    .resolve

class ExamTrackerSpec extends wordspec.AnyWordSpec, matchers.should.Matchers, BeforeAndAfterEach:

  val testKit = ActorTestKit(ExamTrackerSpec.config)

  import ExamTracker.*

  var now = Instant.parse("2023-01-20T10:00:00Z")
  given (() => Instant) = () => now

  var exams = Map.empty[String, ActorRef[Exam.Command]]
  def putExam(id: String, ref: ActorRef[Exam.Command]) =
    exams += (id -> ref)
  def putExam(id: String, beh: Behavior[Exam.Command]) =
    exams += (id -> testKit.spawn(beh))

  private val kit = EventSourcedBehaviorTestKit[Command, Event, Tracked](
    testKit.system,
    ExamTracker(ExamConfig(
      preparationPeriodHours = 24,
      trialLengthMinutesRange = (1, 2),
      trackerCheckRateMinutes = 1,
      awakeExamBeforeProceedMinutes = 3
    ), exams(_))
  )

  val manualTime = ManualTime()(using testKit.system)

  override protected def beforeEach(): Unit =
    super.beforeEach()
    kit.clear()

  "ExamTracker" must {

    val prestartAt = ZonedDateTime.parse("2023-01-21T10:00:00Z")
    val startAt = ZonedDateTime.parse("2023-01-22T10:00:00Z")

    def register = kit.runCommand(Register(prestartAt, startAt, "E1"))

    "register new exam" in {
      val result = kit.runCommand(Register(prestartAt, startAt, "E1"))
      result.state.all should contain only
        ExamRef("E1", prestartAt.toInstant, startAt.toInstant, Exam.State.Pending)
      result.event shouldBe Created(prestartAt, startAt, "E1")
    }

    "move exam to upcoming" in {
      register
      val result = kit.runCommand(RegisterStateChange("E1", Exam.State.Upcoming))
      result.event shouldBe StateChanged("E1", Exam.State.Upcoming)
      result.state.all should contain only
        ExamRef("E1", prestartAt.toInstant, startAt.toInstant, Exam.State.Upcoming)
    }

    "move exam to 'in progress'" in {
      register
      val res0 = kit.runCommand(RegisterStateChange("E1", Exam.State.Upcoming))
      res0.state.all should contain only
        ExamRef("E1", prestartAt.toInstant, startAt.toInstant, Exam.State.Upcoming)
      val result = kit.runCommand(RegisterStateChange("E1", Exam.State.InProgress))
      result.event shouldBe StateChanged("E1", Exam.State.InProgress)
      result.state.all shouldBe empty
    }

    "move exam to cancelled from pending" in {
      register
      val result = kit.runCommand(RegisterStateChange("E1", Exam.State.Cancelled))
      result.event shouldBe StateChanged("E1", Exam.State.Cancelled)
      result.state.all shouldBe empty
    }
    
    "move exam to cancelled from upcoming" in {
      register
      val res0 = kit.runCommand(RegisterStateChange("E1", Exam.State.Upcoming))
      res0.state.all should contain only
        ExamRef("E1", prestartAt.toInstant, startAt.toInstant, Exam.State.Upcoming)
      val result = kit.runCommand(RegisterStateChange("E1", Exam.State.Cancelled))
      result.event shouldBe StateChanged("E1", Exam.State.Cancelled)
      result.state.all shouldBe empty
    }

    "wake exam before transition to upcoming" in {
      register
      val probe = testKit.createTestProbe[Exam.Command]()
      putExam("E1", probe.ref)
      now = Instant.parse("2023-01-21T09:57:00Z")
      probe.expectNoMessage()
      manualTime.timePasses(1.minutes)
      probe.expectMessage(Exam.Awake)
    }

    "wake exam before transition to 'in progress'" in {
      register
      kit.runCommand(RegisterStateChange("E1", Exam.State.Upcoming))
      val probe = testKit.createTestProbe[Exam.Command]()
      putExam("E1", probe.ref)
      now = Instant.parse("2023-01-22T09:57:00Z")
      probe.expectNoMessage()
      manualTime.timePasses(1.minutes)
      probe.expectMessage(Exam.Awake)
    }


  }
