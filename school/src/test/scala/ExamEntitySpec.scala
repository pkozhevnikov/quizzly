package quizzly.school

import java.util.UUID
import java.time.*

import akka.actor.typed.*
import akka.actor.typed.scaladsl.*
import akka.actor.testkit.typed.scaladsl.*
import akka.persistence.testkit.scaladsl.{EventSourcedBehaviorTestKit as TestKit}
import akka.cluster.sharding.typed.scaladsl.*
import akka.cluster.sharding.typed.testkit.scaladsl.*

import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext

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
      BeforeAndAfterEach,
      BeforeAndAfterAll:

  given ExecutionContext = system.executionContext

  val id = "exam-1"

  import Exam.*

  private var factm = Map.empty[QuizID, EntityRef[QuizFact.Command]]
  private def putFact(id: QuizID, beh: Behavior[QuizFact.Command]) =
    factm += (id -> TestEntityRef(QuizFact.EntityKey, id, plainKit.spawn(beh)))
  given (QuizID => EntityRef[QuizFact.Command]) = factm(_)

  private val kit = TestKit[Command, Event, Quiz](system, ExamEntity(id, ExamConfig(24)))
  private val plainKit = ActorTestKit()

  override def beforeEach(): Unit =
    super.beforeEach()
    kit.clear()
  override def afterAll(): Unit = plainKit.shutdownTestKit()

  import Resp.*

  val student1 = Student("stud1", "stud1 name")
  val student2 = Student("stud2", "stud2 name")
  val student3 = Student("stud3", "stud3 name")
  val student4 = Student("stud4", "stud4 name")
  val student5 = Student("stud5", "stud5 name")

  val official1 = Official("off1", "off1 name")
  val official2 = Official("off2", "off2 name")
  val official3 = Official("off3", "off3 name")
  val official4 = Official("off4", "off4 name")
  val official5 = Official("off5", "off5 name")

  "Exam entity" when {

    "Blank" must {

      "reject all commands except create" in {
        def rejected(cmds: (ActorRef[Resp[_]] => Command)*) = cmds.foreach { cmd =>
          val result = kit.runCommand(cmd(_))
          result.reply shouldBe Bad(examNotFound.error())
          result.state shouldBe Blank()
        }

        rejected(IncludeTestees(Set.empty, _), ExcludeTestees(Set.empty, _), SetTrialLength(1, _))

        val result = kit.runCommand(Proceed)
        result.hasNoEvents shouldBe true
        result.state shouldBe Blank()
      }

      val period = ExamPeriod(
        ZonedDateTime.parse("2022-11-23T10:11:12Z"),
        ZonedDateTime.parse("2022-11-30T11:12:13Z")
      )
      
      "be created" in {
        val quiz = Quiz(UUID.randomUUID.toString, "test quiz")
        putFact(
          quiz.id,
          Behaviors.receiveMessage[QuizFact.Command] { 
            case QuizFact.Use("exam-1", replyTo) =>
              replyTo ! Good(quiz)
              Behaviors.stopped
            case _ => Behaviors.stopped
          }
        )
        val prepStart = ZonedDateTime.parse("2022-11-22T10:11:12Z")
        val result = kit
          .runCommand(Create(quiz.id, 60, period, Set(student1, student2, official3), official4, _))
        result.reply shouldBe Good(CreateExamDetails(prepStart, official4))
        result.state shouldBe
          Pending(
            quiz,
            60,
            prepStart,
            period,
            Set(student1, student2, official3),
            official4
          )
      }

      def reject(reason: Reason) =
        val quizID = UUID.randomUUID.toString
        putFact(quizID, Behaviors.receiveMessage[QuizFact.Command] {
          case QuizFact.Use("exam-1", replyTo) =>
            replyTo ! Bad(reason.error())
            Behaviors.stopped
          case _ => 
            Behaviors.stopped
        })
        val result = kit.runCommand(Create(quizID, 60, period, Set.empty, official1, _))
        result.reply shouldBe Bad(reason.error())
        result.state shouldBe Blank()

      "reject creation if quiz is obsolete" in {
        reject(QuizFact.isObsolete)
      }

      "reject creation if quiz is used by this exam" in {
        reject(QuizFact.isAlreadyUsed)
      }

      "reject creation if quiz had ever been published" in {
        reject(QuizFact.wasPublished)
      }

      "reject creation if quiz is now published" in {
        reject(QuizFact.wasPublished)
      }

    }

    "Pending" ignore {}

    "Upcoming" ignore {}

    "InProgress" ignore {}

    "Ended" ignore {}

    "Cancelled" ignore {}

  }
