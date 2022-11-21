package quizzly.school

import akka.actor.typed.ActorRef
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.{EventSourcedBehaviorTestKit as TestKit}
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit.*

import org.scalatest.*
import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.*

object QuizFactSpec:
  val config: Config = ConfigFactory
    .parseString("""
      akka.actor {
        serialization-bindings {
          "quizzly.school.CborSerializable" = jackson-cbor
        }
      }
      """)
    .withFallback(TestKit.config)

class QuizFactSpec
    extends ScalaTestWithActorTestKit(QuizFactSpec.config),
      AnyWordSpecLike,
      BeforeAndAfterEach:

  val id = "fact-1"

  import QuizFact.*

  private val kit = TestKit[Command, Event, Option[Fact]](
    system,
    QuizFact(
      id,
      ExamConfig()
    )
  )

  override protected def beforeEach(): Unit =
    super.beforeEach()
    kit.clear()

  "QuizFact" must {
    
    def ignored(result: CommandResult[Command, Event, Option[Fact]]) = 
      result.hasNoEvents shouldBe true
      result.state shouldBe None

    "ignore any command except initialization" ignore {
      val result1 = kit.runCommand(Use("eid", _))
      result1.reply shouldBe Resp.Bad(Reason.notFound.error())
      result1.hasNoEvents shouldBe true
      result1.state shouldBe None

      result1.replyOfType[Resp[_]] match
        case Resp.OK => println("ok")
        case Resp.Bad(e) => e.reason match
          case Reason.notFound => println("not found")
          case Reason.isNotPublished => println("not published")
      //ignored(kit.runCommand(Use("eid", _)))
      //ignored(kit.runCommand(Publish))
      //ignored(kit.runCommand(Unpublish))
      //ignored(kit.runCommand(SetObsolete))
    }

    "be initialized" in {
      val result = kit.runCommand(Init("quiz1", false))
      result.event shouldBe Inited("quiz1", false)
      result.state shouldBe Some(Fact("quiz1", false, false, false, Set.empty))
    }

    "ignore initialization if already initialized" ignore {
      //ignored(kit.runCommand(Init("t", false)))
    }

    def init = kit.runCommand(Init("quiz1", false))

    "be used" ignore {
      //init
      //val result = kit.runCommand(Use("exam1", _))
      //result.reply shouldBe UseReply.OK
      //result.stateOfType[Fact].usedBy shouldBe Set("exam1")
    }

    "reject usage if had ever been published" ignore {
      //init
      //kit.runCommand(Publish)
    }

    "reject usage if is obsolete" ignore {
    }

    "reject usage if not initialized" ignore {
    }

    "be published" ignore {
    }

    "reject publication if already published" ignore {
    }

    "reject publication if is ignore use" ignore {
    }

    "be unpublished" ignore {
    }

    "reject unpublication if not published" ignore {
    }

    "be set obsolete" ignore {
    }

  }
