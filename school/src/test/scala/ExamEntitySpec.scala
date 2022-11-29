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
    factm += (id -> TestEntityRef(QuizFact.EntityKey, id, testKit.spawn(beh)))
  given (() => Instant) = () => Instant.parse("2022-11-01T00:00:00Z")

  private val factKit = TestKit[Command, Event, Quiz](
    system,
    ExamEntity(
      id,
      factm(_),
      ExamConfig(preparationPeriodHours = 48, trialLengthMinutesRange = (5, 180))
    )
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    factKit.clear()
  override def afterAll(): Unit = testKit.shutdownTestKit()

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

  val period = ExamPeriod(
    ZonedDateTime.parse("2022-11-23T10:11:12Z"),
    ZonedDateTime.parse("2022-11-30T11:12:13Z")
  )
  val prepStart = ZonedDateTime.parse("2022-11-21T10:11:12Z")

  "Exam entity" when {

    "Blank" must {

      "reject all commands except create" in {
        def rejected(cmds: (ActorRef[Resp[_]] => Command)*) = cmds.foreach { cmd =>
          val result = factKit.runCommand(cmd(_))
          result.reply shouldBe Bad(examNotFound.error())
          result.state shouldBe Blank()
        }

        rejected(IncludeTestees(Set.empty, _), ExcludeTestees(Set.empty, _), SetTrialLength(1, _))

        val result = factKit.runCommand(Proceed)
        result.hasNoEvents shouldBe true
        result.state shouldBe Blank()
      }

      "be created" in {
        val quiz = Quiz(UUID.randomUUID.toString, "test quiz")
        putFact(
          quiz.id,
          Behaviors.receiveMessage[QuizFact.Command] {
            case QuizFact.Use("exam-1", replyTo) =>
              replyTo ! Good(quiz)
              Behaviors.stopped
            case _ =>
              Behaviors.stopped
          }
        )
        val result = factKit
          .runCommand(Create(quiz.id, 60, period, Set(student1, student2, official3), official4, _))
        result.reply shouldBe Good(CreateExamDetails(prepStart, official4))
        result.state shouldBe
          Pending(quiz, 60, prepStart, period, Set(student1, student2, official3), official4)
      }

      def reject(reason: Reason) =
        val quizID = UUID.randomUUID.toString
        putFact(
          quizID,
          Behaviors.receiveMessage[QuizFact.Command] {
            case QuizFact.Use("exam-1", replyTo) =>
              replyTo ! Bad(reason.error())
              Behaviors.stopped
            case _ =>
              Behaviors.stopped
          }
        )
        val result = factKit.runCommand(Create(quizID, 60, period, Set.empty, official1, _))
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

      "reject creation if bad period" in {
        val result = factKit.runCommand(
          Create(
            "q",
            50,
            ExamPeriod(
              ZonedDateTime.parse("2022-11-12T10:00:00Z"),
              ZonedDateTime.parse("2022-11-10T10:00:00Z")
            ),
            Set.empty,
            official1,
            _
          )
        )
        result.reply shouldBe Bad(badExamPeriod.error())
        result.state shouldBe Blank()
      }

      "reject creation if period start too soon" in {
        val result = factKit.runCommand(
          Create(
            "q",
            50,
            ExamPeriod(
              ZonedDateTime.parse("2022-11-02T23:59:59Z"),
              ZonedDateTime.parse("2022-11-03T12:00:00Z")
            ),
            Set.empty,
            official1,
            _
          )
        )
        result.reply shouldBe Bad(examTooSoon.error() + "prep time hours" + "48")
        result.state shouldBe Blank()
      }

      "reject creation if trial length is not in range" in {
        val result1 = factKit.runCommand(Create("q", 4, period, Set.empty, official1, _))
        result1.reply shouldBe Bad(badTrialLength.error() + "5" + "180")
        result1.state shouldBe Blank()

        val result2 = factKit.runCommand(Create("q", 181, period, Set.empty, official1, _))
        result2.reply shouldBe Bad(badTrialLength.error() + "5" + "180")
        result2.state shouldBe Blank()
      }

    }

    val quiz = Quiz(UUID.randomUUID.toString, "test quiz")
    putFact(
      quiz.id,
      Behaviors.receiveMessage[QuizFact.Command] {
        case QuizFact.Use("exam-1", replyTo) =>
          replyTo ! Good(quiz)
          Behaviors.same
        case _ =>
          Behaviors.stopped
      }
    )

    def init =
      val result = factKit.runCommand(Create(quiz.id, 60, period, Set.empty, official1, _))
      val initial = Pending(quiz, 60, prepStart, period, Set.empty, official1)
      result.state shouldBe initial
      initial

    "Pending" must {

      "include testees" in {
        val initial = init
        val result1 = factKit.runCommand(IncludeTestees(Set(student1, student2, official2), _))
        result1.reply shouldBe OK
        result1.state shouldBe initial.copy(testees = Set(student1, student2, official2))
      }

      "exclude testees" in {
        val initial = init
        factKit.runCommand(IncludeTestees(Set(student1, student2, official2), _))
        val result = factKit.runCommand(ExcludeTestees(Set(student1, official2, student3), _))
        result.reply shouldBe OK
        result.state shouldBe initial.copy(testees = Set(student2))
      }

      "set trial length" in {
        val initial = init
        val result = factKit.runCommand(SetTrialLength(90, _))
        result.reply shouldBe OK
        result.state shouldBe initial.copy(trialLengthMinutes = 90)
      }

      "proceed to upcoming" in {
        val initial = init
        val result = factKit.runCommand(Proceed)
        result.event shouldBe GoneUpcoming
        result.state shouldBe
          Upcoming(
            initial.quiz,
            initial.trialLengthMinutes,
            initial.period,
            initial.testees,
            initial.host
          )
      }

      "be cancelled" in {
        val initial = init
        val at = Instant.now()
        val result = factKit.runCommand(Cancel(at, _))
        result.reply shouldBe OK
        result.state shouldBe
          Cancelled(
            initial.quiz,
            initial.trialLengthMinutes,
            initial.period,
            initial.testees,
            initial.host,
            at
          )
      }

      "reject creation" in {
        val initial = init
        val result = factKit.runCommand(Create("", 1, period, Set.empty, official1, _))
        result.reply shouldBe Bad(illegalState.error() + "Pending")
        result.state shouldBe initial
      }

    }

    "Upcoming" must {

      "be cancelled" in {
        val initial = init
        val result0 = factKit.runCommand(Proceed)
        result0.state shouldBe
          Upcoming(
            initial.quiz,
            initial.trialLengthMinutes,
            initial.period,
            initial.testees,
            initial.host
          )

        val at = Instant.now()
        val result = factKit.runCommand(Cancel(at, _))
        result.reply shouldBe OK
        result.state shouldBe
          Cancelled(
            initial.quiz,
            initial.trialLengthMinutes,
            initial.period,
            initial.testees,
            initial.host,
            at
          )
      }

      "reject any other action" in {
        val initial = init
        factKit.runCommand(Proceed)
        def rejected(cmds: (ActorRef[Resp[_]] => Command)*) = cmds.foreach { cmd =>
          val result = factKit.runCommand(cmd(_))
          result.reply shouldBe Bad(illegalState.error() + "Upcoming")
        }

        rejected(
          Create("", 1, period, Set.empty, official1, _),
          IncludeTestees(Set.empty, _),
          ExcludeTestees(Set.empty, _),
          SetTrialLength(2, _)
        )
      }

      "proceed to in progress" in {
        val pending = init
        factKit.runCommand(Proceed)
        val result = factKit.runCommand(Proceed)
        result.event shouldBe GoneInProgress
        result.state shouldBe
          InProgress(
            quiz,
            pending.trialLengthMinutes,
            pending.period,
            pending.testees,
            pending.host
          )
      }

    }

    "InProgress" must {
      
      "be cancelled" in {
        val initial = init
        factKit.runCommand(Proceed)
        val result0 = factKit.runCommand(Proceed)
        result0.state shouldBe
          InProgress(
            initial.quiz,
            initial.trialLengthMinutes,
            initial.period,
            initial.testees,
            initial.host
          )

        val at = Instant.now()
        val result = factKit.runCommand(Cancel(at, _))
        result.reply shouldBe OK
        result.state shouldBe
          Cancelled(
            initial.quiz,
            initial.trialLengthMinutes,
            initial.period,
            initial.testees,
            initial.host,
            at
          )
      }

      "reject any other action" in {
        val initial = init
        factKit.runCommand(Proceed)
        factKit.runCommand(Proceed)

        def rejected(cmds: (ActorRef[Resp[_]] => Command)*) = cmds.foreach { cmd =>
          val result = factKit.runCommand(cmd(_))
          result.reply shouldBe Bad(illegalState.error() + "InProgress")
        }

        rejected(
          Create("", 1, period, Set.empty, official1, _),
          IncludeTestees(Set.empty, _),
          ExcludeTestees(Set.empty, _),
          SetTrialLength(2, _)
        )
      }

      "proceed to ended" in {
        val pending = init
        factKit.runCommand(Proceed)
        factKit.runCommand(Proceed)
        val result = factKit.runCommand(Proceed)
        result.event shouldBe GoneEnded
        result.state shouldBe
          Ended(
            quiz,
            pending.trialLengthMinutes,
            pending.period,
            pending.testees,
            pending.host
          )
      }

    }

    "Ended" must {
      "reject any action" in {
        val initial = init
        factKit.runCommand(Proceed)
        factKit.runCommand(Proceed)
        factKit.runCommand(Proceed)

        def rejected(cmds: (ActorRef[Resp[_]] => Command)*) = cmds.foreach { cmd =>
          val result = factKit.runCommand(cmd(_))
          result.reply shouldBe Bad(illegalState.error() + "Ended")
        }

        rejected(
          Create("", 1, period, Set.empty, official1, _),
          IncludeTestees(Set.empty, _),
          ExcludeTestees(Set.empty, _),
          SetTrialLength(2, _)
        )

        val result = factKit.runCommand(Proceed)
        result.hasNoEvents shouldBe true
      }
    }

    "Cancelled" must {
      "reject any action" in {
        val initial = init
        factKit.runCommand(Cancel(Instant.now(), _))

        def rejected(cmds: (ActorRef[Resp[_]] => Command)*) = cmds.foreach { cmd =>
          val result = factKit.runCommand(cmd(_))
          result.reply shouldBe Bad(illegalState.error() + "Cancelled")
        }

        rejected(
          Create("", 1, period, Set.empty, official1, _),
          IncludeTestees(Set.empty, _),
          ExcludeTestees(Set.empty, _),
          SetTrialLength(2, _)
        )

        val result = factKit.runCommand(Proceed)
        result.hasNoEvents shouldBe true
      }
    }

  }
