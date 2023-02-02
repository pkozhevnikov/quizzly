package quizzly.school

import akka.actor.typed.ActorRef
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.persistence.testkit.scaladsl.{EventSourcedBehaviorTestKit as TestKit}
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit.*

import org.scalatest.*

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

class QuizFactSpec extends wordspec.AnyWordSpec, matchers.should.Matchers, BeforeAndAfterEach:

  val testKit = ActorTestKit(QuizFactSpec.config)

  val id = "fact-1"

  import QuizFact.*

  private val kit = TestKit[Command, Event, Option[Fact]](
    testKit.system,
    QuizFact(id, ExamConfig(24, (1, 2)))
  )

  override protected def beforeEach(): Unit =
    super.beforeEach()
    kit.clear()

  "QuizFact" must {

    import Resp.*

    def ignored(reason: Reason, cmds: (ActorRef[Resp[?]] => Command)*) = cmds.foreach { cmd =>
      val result = kit.runCommand(cmd(_))
      result.hasNoEvents shouldBe true
      result.reply shouldBe Bad(reason.error())
      result.state shouldBe None
    }

    val full = FullQuiz(
      "quiz1",
      "quiz1 title",
      "quiz1 intro",
      45,
      PersonRef("", ""),
      Set.empty,
      Set.empty,
      List.empty
    )

    "ignore any command except initialization" in {
      ignored(notFound, Use("eid", _), Publish(_), Unpublish(_))

      val result = kit.runCommand(SetObsolete)
      result.hasNoEvents shouldBe true
      result.state shouldBe None
    }

    "be initialized" in {
      val result = kit.runCommand(Init(full))
      result.event shouldBe Inited(full)
      result.state shouldBe Some(Fact(full, false, false, false, Set.empty))
    }

    "ignore initialization if already initialized" in {
      init
      val result = kit.runCommand(Init(full))
      result.hasNoEvents shouldBe true
      result.state shouldBe Some(Fact(full, false, false, false, Set.empty))
    }

    def init = kit.runCommand(Init(full))

    "be used" in {
      init
      val result = kit.runCommand(Use("exam1", _))
      result.reply shouldBe Good(Quiz("fact-1", "quiz1 title"))
      result.stateOfType[Option[Fact]].get.usedBy shouldBe Set("exam1")
    }

    "stop usage" in {
      init
      kit.runCommand(Use("exam1", _))
      val result = kit.runCommand(Use("exam2", _))
      result.stateOfType[Option[Fact]].get.usedBy shouldBe Set("exam1", "exam2")
      val result2 = kit.runCommand(StopUse("exam1"))
      result2.events shouldBe Seq(UseStopped("exam1"))
      result2.stateOfType[Option[Fact]].get.usedBy shouldBe Set("exam2")

      val result3 = kit.runCommand(StopUse("exam2"))
      result3.events shouldBe Seq(UseStopped("exam2"), GotUnused)
      result3.stateOfType[Option[Fact]].get.usedBy shouldBe empty
    }

    "reject usage if had ever been published" in {
      init
      kit.runCommand(Publish(_))
      kit.runCommand(Unpublish(_))
      val result = kit.runCommand(Use("exam1", _))
      result.reply shouldBe Bad(wasPublished.error())
      result.state shouldBe Some(Fact(full, false, true, false, Set.empty))
    }

    "reject usage if is obsolete" in {
      init
      kit.runCommand(SetObsolete)
      val result = kit.runCommand(Use("exam1", _))
      result.reply shouldBe Bad(isObsolete.error())
      result.state shouldBe Some(Fact(full, true, false, false, Set.empty))
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
      result.state shouldBe Some(Fact(full, false, true, true, Set.empty))
    }

    "reject publication if already published" in {
      init
      kit.runCommand(Publish(_))
      val result = kit.runCommand(Publish(_))
      result.reply shouldBe Bad(wasPublished.error())
      result.state shouldBe Some(Fact(full, false, true, true, Set.empty))
    }

    "reject publication if is in use" in {
      init
      kit.runCommand(Use("exam1", _))
      val result = kit.runCommand(Publish(_))
      result.reply shouldBe Bad(isUsed.error())
      result.state shouldBe Some(Fact(full, false, false, false, Set("exam1")))
    }

    "be unpublished" in {
      init
      kit.runCommand(Publish(_))
      val result = kit.runCommand(Unpublish(_))
      result.reply shouldBe Resp.OK
      result.state shouldBe Some(Fact(full, false, true, false, Set.empty))
    }

    "reject unpublication if not published" in {
      init
      val result = kit.runCommand(Unpublish(_))
      result.reply shouldBe Bad(isNotPublished.error())
      result.state shouldBe Some(Fact(full, false, false, false, Set.empty))
    }

    "be set obsolete" in {
      init
      val result = kit.runCommand(SetObsolete)
      result.event shouldBe GotObsolete
      result.state shouldBe Some(Fact(full, true, false, false, Set.empty))
    }

  }
