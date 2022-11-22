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

  private val kit = TestKit[Command, Event, Option[Fact]](system, QuizFact(id, ExamConfig(24)))

  override protected def beforeEach(): Unit =
    super.beforeEach()
    kit.clear()

  "QuizFact" must {

    import Resp.*

    def ignored(reason: Reason, cmds: (ActorRef[Resp[_]] => Command)*) = cmds.foreach { cmd =>
      val result = kit.runCommand(cmd(_))
      result.hasNoEvents shouldBe true
      result.reply shouldBe Bad(reason.error())
      result.state shouldBe None
    }

    "ignore any command except initialization" in {
      ignored(notFound, Use("eid", _), Publish(_), Unpublish(_))

      val result = kit.runCommand(SetObsolete)
      result.hasNoEvents shouldBe true
      result.state shouldBe None
    }

    "be initialized" in {
      val result = kit.runCommand(Init("quiz1", false))
      result.event shouldBe Inited("quiz1", false)
      result.state shouldBe Some(Fact("quiz1", false, false, false, Set.empty))
    }

    "ignore initialization if already initialized" in {
      init
      val result = kit.runCommand(Init("quiz2", false))
      result.hasNoEvents shouldBe true
      result.state shouldBe Some(Fact("quiz1", false, false, false, Set.empty))
    }

    def init = kit.runCommand(Init("quiz1", false))

    "be used" in {
      init
      val result = kit.runCommand(Use("exam1", _))
      result.reply shouldBe Good(Quiz("quiz1", "quiz1 title"))
      result.stateOfType[Option[Fact]].get.usedBy shouldBe Set("exam1")
    }

    "reject usage if had ever been published" in {
      init
      kit.runCommand(Publish(_))
      kit.runCommand(Unpublish(_))
      val result = kit.runCommand(Use("exam1", _))
      result.reply shouldBe Bad(wasPublished.error())
      result.state shouldBe Some(Fact("quiz1", false, true, false, Set.empty))
    }

    "reject usage if is obsolete" in {
      init
      kit.runCommand(SetObsolete)
      val result = kit.runCommand(Use("exam1", _))
      result.reply shouldBe Bad(isObsolete.error())
      result.state shouldBe Some(Fact("quiz1", true, false, false, Set.empty))
    }

    "reject usage if not initialized" in {
      val result = kit.runCommand(Use("exam1", _))
      result.reply shouldBe Bad(notFound.error())
      result.state shouldBe None
    }

    "be published" in {
      init
      val result = kit.runCommand(Publish(_))
      result.reply shouldBe Resp.OK
      result.state shouldBe Some(Fact("quiz1", false, true, true, Set.empty))
    }

    "reject publication if already published" in {
      init
      kit.runCommand(Publish(_))
      val result = kit.runCommand(Publish(_))
      result.reply shouldBe Bad(wasPublished.error())
      result.state shouldBe Some(Fact("quiz1", false, true, true, Set.empty))
    }

    "reject publication if is in use" in {
      init
      kit.runCommand(Use("exam1", _))
      val result = kit.runCommand(Publish(_))
      result.reply shouldBe Bad(isUsed.error())
      result.state shouldBe Some(Fact("quiz1", false, false, false, Set("exam1")))
    }

    "be unpublished" in {
      init
      kit.runCommand(Publish(_))
      val result = kit.runCommand(Unpublish(_))
      result.reply shouldBe Resp.OK
      result.state shouldBe Some(Fact("quiz1", false, true, false, Set.empty))
    }

    "reject unpublication if not published" in {
      init
      val result = kit.runCommand(Unpublish(_))
      result.reply shouldBe Bad(isNotPublished.error())
      result.state shouldBe Some(Fact("quiz1", false, false, false, Set.empty))
    }

    "be set obsolete" in {
      init
      val result = kit.runCommand(SetObsolete)
      result.event shouldBe GotObsolete
      result.state shouldBe Some(Fact("quiz1", true, false, false, Set.empty))
    }

  }
