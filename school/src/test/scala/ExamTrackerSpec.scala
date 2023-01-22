package quizzly.school

import akka.actor.typed.ActorRef
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.persistence.testkit.scaladsl.{EventSourcedBehaviorTestKit as TestKit}
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit.*

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
    .withFallback(TestKit.config)
    .resolve

class ExamTrackerSpec extends wordspec.AnyWordSpec, matchers.should.Matchers, BeforeAndAfterEach:

  val testKit = ActorTestKit(ExamTrackerSpec.config)

  import ExamTracker.*

  val now = Instant.parse("2023-01-20T10:00:00Z")
  given (() => Instant) = () => now

  private val kit = TestKit[Command, Event, Tracked](
    testKit.system,
    ExamTracker(ExamConfig(24, (1, 2), 5, 3))
  )

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

    


  }
