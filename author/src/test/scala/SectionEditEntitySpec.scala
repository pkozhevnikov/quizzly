package quizzly.author

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.testkit.typed.scaladsl.ManualTime
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.*
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.testkit.scaladsl.TestEntityRef
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit as TestKit
import com.typesafe.config.*
import org.scalatest.*

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

import scaladsl.Behaviors

object SectionEditEntitySpec:
  val config: Config = ConfigFactory
    .parseString("""
      akka.actor {
        serialization-bindings {
          "quizzly.author.CborSerializable" = jackson-cbor
        }
      }
      """)
    .withFallback(ManualTime.config)
    .withFallback(TestKit.config)

class SectionEditEntitySpec
    extends wordspec.AnyWordSpec,
      matchers.should.Matchers,
      BeforeAndAfterEach:

  val testKit = ActorTestKit(SectionEditEntitySpec.config)
  implicit val system: ActorSystem[?] = testKit.system

  val id = "tq-1-1"

  import Resp.*
  import SectionEdit.*

  val manualTime = ManualTime()

  private var quizm = scala.collection.mutable.Map.empty[SC, EntityRef[Quiz.Command]]
  private def putQuiz(id: QuizID, beh: Behavior[Quiz.Command]) =
    quizm += (id -> TestEntityRef(QuizEntity.EntityKey, id, testKit.spawn(beh)))

  given ExecutionContext = system.executionContext

  private val kit = TestKit[Command, Event, Option[SectionEdit]](
    system,
    SectionEditEntity(
      id,
      quizm(_),
      QuizConfig(
        minAuthors = 2,
        minInspectors = 2,
        minTrialLength = 3,
        minTitleLength = 2,
        inactivityMinutes = 1
      )
    )
  )

  override protected def beforeEach(): Unit =
    super.beforeEach()
    kit.clear()

  val author1 = Person("author1", "author1 name")
  val author2 = Person("author2", "author2 name")
  val author3 = Person("author3", "author3 name")

  val emptyItem = Item("", "", Statement("", None), List.empty, false, List.empty)

  val item = Item(
    "1",
    "item1",
    Statement("stmt1-1", Some("img1-1")),
    List(List(Statement("hint1-1", None))),
    true,
    List(1)
  )
  val section0 = Section("tq-1-1", "section title", "section intro", List.empty)
  val section = section0.copy(items = List(item))

  "SectionEditEntity" when {

    "not created yet" must {

      "be created" in {
        val result = kit.runCommand(Create(section.title, author1, "tq-1", _))
        result.reply shouldBe Resp.OK
        result.event shouldBe a[Created]
        result.state shouldBe Some(SectionEdit(Some(author1), section0.copy(intro = ""), "tq-1"))
      }

      "reject create if short title" in {
        val result = kit.runCommand(Create("x", author1, "t1-1", _))
        result.reply shouldBe Bad(Quiz.tooShortTitle.error())
        result.hasNoEvents
      }

      "reject other actions" in {
        def rejected(cmds: (ActorRef[Resp[?]] => Command)*) = cmds.foreach { cmd =>
          val result = kit.runCommand(cmd(_))
          result.reply shouldBe Bad(Quiz.sectionNotFound.error())
        }

        rejected(
          Update(author1, "title", "intro", _),
          Own(author1, _),
          AddItem(author1, _),
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

      def rejectAuthor(cmds: (ActorRef[Resp[?]] => Command)*) = cmds.foreach { cmd =>
        val result = kit.runCommand(cmd(_))
        result.reply shouldBe Bad(notOwner.error())
        result.hasNoEvents shouldBe true
      }

      "reject action by other author" in {
        create
        rejectAuthor(Update(author2, "", "", _), Discharge(author2, _))
      }

      "be updated" in {
        create
        val result = kit.runCommand(Update(author1, "new title", "new intro", _))
        result.reply shouldBe Resp.OK
        result.state shouldBe
          Some(
            SectionEdit(
              Some(author1),
              section0.copy(title = "new title", intro = "new intro"),
              "tq-1"
            )
          )
      }

      "reject update if title short" in {
        create
        val result = kit.runCommand(Update(author1, "x", "y", _))
        result.reply shouldBe Bad(Quiz.tooShortTitle.error())
        result.hasNoEvents
      }

      "be discharged" in {
        create
        putQuiz(
          "tq-1",
          Behaviors.receiveMessage { case c: Quiz.SaveSection =>
            c.replyTo ! Good(c.section.sc)
            Behaviors.stopped
          }
        )
        val result = kit.runCommand(Discharge(author1, _))
        result.reply shouldBe Resp.OK
        result.stateOfType[Option[SectionEdit]].get.owner shouldBe None
      }

      "add item" in {
        create
        val result = kit.runCommand(AddItem(author1, _))
        result.reply shouldBe Good("1")
        result.event shouldBe ItemSaved(emptyItem.copy(sc = "1"))
        result.stateOfType[Option[SectionEdit]].get.section.items shouldBe
          List(emptyItem.copy(sc = "1"))
      }

      "save item" in {
        create
        kit.runCommand(AddItem(author1, _))
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

      "reject remove item if not found" in {
        create
        val result = kit.runCommand(RemoveItem(author1, "xyz", _))
        result.reply shouldBe Bad(itemNotFound.error() + "xyz")
        result.hasNoEvents shouldBe true
      }

      "reject move item if not found" in {
        create
        val result = kit.runCommand(MoveItem(author1, "xyz", true, _))
        result.reply shouldBe Bad(itemNotFound.error() + "xyz")
        result.hasNoEvents shouldBe true
      }

      "reject move item if it's on top or bottom" in {
        create
        kit.runCommand(SaveItem(author1, item, _))

        val result1 = kit.runCommand(MoveItem(author1, "1", true, _))
        result1.reply shouldBe Bad(cannotMove.error())
        result1.hasNoEvents shouldBe true

        val result2 = kit.runCommand(MoveItem(author1, "1", false, _))
        result2.reply shouldBe Bad(cannotMove.error())
        result2.hasNoEvents shouldBe true
      }

      "move item" in {
        create
        kit.runCommand(AddItem(author1, _))
        kit.runCommand(SaveItem(author1, item, _))
        val item2 = item.copy(sc = "2")
        kit.runCommand(AddItem(author1, _))
        val result0 = kit.runCommand(SaveItem(author1, item2, _))
        result0.stateOfType[Option[SectionEdit]].get.section.items shouldBe List(item, item2)

        val result1 = kit.runCommand(MoveItem(author1, "2", true, _))
        result1.reply shouldBe Good(List("2", "1"))
        result1.stateOfType[Option[SectionEdit]].get.section.items shouldBe List(item2, item)

        val result2 = kit.runCommand(MoveItem(author1, "2", false, _))
        result2.reply shouldBe Good(List("1", "2"))
        result2.stateOfType[Option[SectionEdit]].get.section.items shouldBe List(item, item2)
      }

      "get owner" in {
        create
        val result = kit.runCommand(GetOwner(_))
        result.reply shouldBe Good(Some(author1))
        result.hasNoEvents
      }

      "discharge after inactivity period" in {
        val probe = testKit.createTestProbe[Quiz.Command]()
        putQuiz(
          "tq-1",
          Behaviors.monitor(
            probe.ref,
            Behaviors.receiveMessage { case c: Quiz.SaveSection =>
              c.replyTo ! Good(c.section.sc)
              Behaviors.stopped
            }
          )
        )
        create
        val result1 = kit.runCommand(Own(author2, _))
        result1.reply shouldBe Bad(notOwner.error())
        manualTime.expectNoMessageFor(50.seconds, probe)
        manualTime.timePasses(20.seconds)
        probe.expectMessageType[Quiz.SaveSection]
        val result2 = kit.runCommand(Discharge(author1, _))
        result2.reply shouldBe Bad(notOwned.error())
      }

      "reject ping i not owner" in {
        create
        val result = kit.runCommand(Ping(author2, _))
        result.reply shouldBe (Bad(notOwner.error()))
        result.hasNoEvents shouldBe true
      }

      "respond on ping" in {
        create
        val result = kit.runCommand(Ping(author1, _))
        result.reply shouldBe Resp.OK
        result.hasNoEvents shouldBe true
      }

    }

    "is not owned" must {

      "reject create if exists" in {
        kit.runCommand(Create(section.title, author1, "tq-1", _))
        val result = kit.runCommand(Create(section.title, author1, "tq-1", _))
        result.reply shouldBe Bad(alreadyExists.error())
      }

      def createDischarged =
        create
        putQuiz(
          "tq-1",
          Behaviors.receiveMessage { case c: Quiz.SaveSection =>
            c.replyTo ! Good(c.section.sc)
            Behaviors.stopped
          }
        )
        val result = kit.runCommand(Discharge(author1, _))
        result.reply shouldBe Resp.OK
        result.stateOfType[Option[SectionEdit]].get.owner shouldBe None

      "be owned" in {
        createDischarged
        val result1 = kit.runCommand(Own(author2, _))
        result1.reply shouldBe Resp.OK
        result1.stateOfType[Option[SectionEdit]].get.owner shouldBe Some(author2)
      }

      "get owner" in {
        createDischarged
        val result = kit.runCommand(GetOwner(_))
        result.reply shouldBe Good(None)
        result.hasNoEvents
      }

      "reject other actions" in {
        createDischarged
        def rejected(cmds: (ActorRef[Resp[?]] => Command)*) = cmds.foreach { cmd =>
          val result = kit.runCommand(cmd(_))
          result.reply shouldBe Bad(notOwned.error())
        }
        rejected(
          Update(author1, "title", "intro", _),
          AddItem(author1, _),
          SaveItem(author1, item, _),
          RemoveItem(author1, "", _),
          MoveItem(author1, "", false, _),
          Discharge(author1, _),
          Ping(author1, _)
        )
      }

    }

  }
