package quizzly.school

import akka.actor.typed.ActorRef
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.{EventSourcedBehaviorTestKit as TestKit}

import org.scalatest.*
import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.*

object ExamEntitySpec:
  val config: Config = ConfigFactory
    .parseString("""
      akka.actor {
        serialization-bindings {
          "quizzly.school.CborSerializable" = jackson-cbor
        }
      }
      """)
    .withFallback(TestKit.config)

class ExamEntitySpec
    extends ScalaTestWithActorTestKit(ExamEntitySpec.config),
      AnyWordSpecLike,
      BeforeAndAfterEach:

  val id = "exam-1"

  import Exam.*

  private val kit = TestKit[Command, Event, Quiz](system, ExamEntity(id, ExamConfig()))

  override protected def beforeEach(): Unit =
    super.beforeEach()
    kit.clear()

  "Exam entity" when {

    "Blank" must {
      "do this" in {}
    }

    "Pending" ignore {}

    "Upcoming" ignore {}

    "InProgress" ignore {}

    "Ended" ignore {}

    "Cancelled" ignore {}

  }
