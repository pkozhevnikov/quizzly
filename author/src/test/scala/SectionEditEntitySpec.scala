package quizzly.author

import akka.actor.typed.*
import scaladsl.Behaviors
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.{EventSourcedBehaviorTestKit as TestKit}
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.testkit.scaladsl.TestEntityRef

import org.scalatest.*
import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.*

import scala.concurrent.ExecutionContext

object SectionEditEntitySpec:
  val config: Config = ConfigFactory
    .parseString("""
      akka.actor {
        serialization-bindings {
          "quizzly.author.CborSerializable" = jackson-cbor
        }
      }
      """)
    .withFallback(TestKit.config)

class SectionEditEntitySpec
    extends ScalaTestWithActorTestKit(SectionEditEntitySpec.config),
      AnyWordSpecLike,
      BeforeAndAfterEach:

  val id = "tq-1-1"

  import SectionEdit.*
  import Resp.*

  private var quizm = scala.collection.mutable.Map.empty[SC, EntityRef[Quiz.Command]]
  private def putQuiz(id: QuizID, beh: Behavior[Quiz.Command]) =
    quizm += (id -> TestEntityRef(QuizEntity.EntityKey, id, testKit.spawn(beh)))

  given ExecutionContext = system.executionContext

  private val kit = TestKit[Command, Event, Option[SectionEdit]](
    system,
    SectionEditEntity(
      id,
      quizm(_),
      QuizConfig(minAuthors = 2, minInspectors = 2, minTrialLength = 3, minTitleLength = 2)
    )
  )

  override protected def beforeEach(): Unit =
    super.beforeEach()
    kit.clear()

  val author1 = Person("author1", "author1 name")
  val author2 = Person("author2", "author2 name")
  val author3 = Person("author3", "author3 name")

  val item = Item(
    "1",
    "item1",
    Statement("stmt1-1", Some("img1-1")),
    List(List(Statement("hint1-1", None))),
    true,
    List(1)
  )
  val section0 = Section("tq-1-1", "section title", List.empty)
  val section = section0.copy(items = List(item))

  "SectionEditEntity" when {

    "not created yet" must {

      "be created" in {
        val result = kit.runCommand(Create(section.title, author1, "tq-1", _))
        result.reply shouldBe Resp.OK
        result.event shouldBe a[Created]
        result.state shouldBe Some(SectionEdit(Some(author1), section0, "tq-1", 0))
      }

      "reject other actions" in {
        def rejected(cmds: (ActorRef[Resp[_]] => Command)*) = cmds.foreach { cmd =>
          val result = kit.runCommand(cmd(_))
          result.reply shouldBe Bad(Quiz.sectionNotFound.error())
        }

        rejected(
          Update(author1, "title", _),
          Own(author1, _),
          NextItemSC(author1, _),
          SaveItem(author1, item, _),
          RemoveItem(author1, "", _),
          MoveItem(author1, "", false, _),
          Discharge(author1, _),
          GetOwner(_),
          Ping(author1, _)
        )
      }

    }

    def create = kit.runCommand(Create(section.title, author1, "tq-1", _))

    "is owned" must {

      "reject create if exists" in {
        create
        val result = kit.runCommand(Create(section.title, author1, "tq-1", _))
        result.reply shouldBe Bad(alreadyExists.error())
      }
      
      def rejectAuthor(cmds: (ActorRef[Resp[_]] => Command)*) = cmds.foreach { cmd =>
        val result = kit.runCommand(cmd(_))
        result.reply shouldBe Bad(notOwner.error())
        result.hasNoEvents shouldBe true
      }

      "reject action by other author" in {
        create
        rejectAuthor(
          Update(author2, "", _),
          Discharge(author2, _)
        )
      }

      "be updated" in {
        create
        val result = kit.runCommand(Update(author1, "new title", _))
        result.reply shouldBe Resp.OK
        result.state shouldBe Some(SectionEdit(Some(author1), section0.copy(title = "new title"), "tq-1", 0))
      }

      "be discharged" in {
        create
        putQuiz("tq-1", Behaviors.receiveMessage {
          case c: Quiz.SaveSection => 
            c.replyTo ! Resp.OK
            Behaviors.stopped
        })
        val result = kit.runCommand(Discharge(author1, _))
        result.reply shouldBe Resp.OK
        result.stateOfType[Option[SectionEdit]].get.owner shouldBe None
      }

      "return next item sc" in {
        create
        val result = kit.runCommand(NextItemSC(author1, _))
        result.reply shouldBe Good("1")
        result.event shouldBe SCIncrement
        result.stateOfType[Option[SectionEdit]].get.scCounter shouldBe 1
      }

      "save item" in {
        create
        val result = kit.runCommand(SaveItem(author1, item, _))
        result.reply shouldBe Resp.OK
        result.event shouldBe a[ItemSaved]
        result.stateOfType[Option[SectionEdit]].get.section.items shouldBe List(item)
      }

      "reject save item if not found" in {
        create
        val result = kit.runCommand(RemoveItem(author1, "xyz", _))
        result.reply shouldBe Bad(itemNotFound.error() + "xyz")
        result.hasNoEvents shouldBe true
      }

      "remove item" in {
        create
        kit.runCommand(SaveItem(author1, item, _))
        val result = kit.runCommand(RemoveItem(author1, "1", _))
        result.reply shouldBe Resp.OK
        result.stateOfType[Option[SectionEdit]].get.section.items shouldBe List.empty[Item]
      }

    }

    "is not owned" must {

      "reject create if exists" in {
        kit.runCommand(Create(section.title, author1, "tq-1", _))
        kit.runCommand(Discharge(author1, _))
        val result = kit.runCommand(Create(section.title, author1, "tq-1", _))
        result.reply shouldBe Bad(alreadyExists.error())
      }

      "be owned" in {
        create
        val result = kit.runCommand(Discharge(author2, _))
        result.reply shouldBe Resp.OK
        result.stateOfType[Option[SectionEdit]].get.owner shouldBe Some(author2)
      }

    }

  }
