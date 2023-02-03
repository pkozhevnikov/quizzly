package quizzly.school

import java.util.UUID
import java.time.*
import java.time.temporal.ChronoUnit;

import akka.actor.typed.*
import akka.actor.typed.scaladsl.*
import akka.actor.testkit.typed.scaladsl.*
import akka.persistence.testkit.scaladsl.{EventSourcedBehaviorTestKit as TestKit}
import akka.cluster.sharding.typed.scaladsl.*
import akka.cluster.sharding.typed.testkit.scaladsl.*

import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext

import org.scalatest.*

import com.typesafe.config.*

object ExamEntitySpec:
  val config: Config =
    ConfigFactory
      .parseString("""
      akka.actor {
        serialization-bindings {
          "quizzly.school.CborSerializable" = jackson-cbor
        }
      }
      """)
      .withFallback(TestKit.config)
      .withFallback(ManualTime.config)
      .resolve

class ExamEntitySpec
    extends wordspec.AnyWordSpec,
      matchers.should.Matchers,
      BeforeAndAfterEach,
      BeforeAndAfterAll:

  val testKit = ActorTestKit(ExamEntitySpec.config)
  given ExecutionContext = testKit.system.executionContext

  val id = "exam-1"

  import Exam.*

  var tracker = testKit.createTestProbe[ExamTracker.Command]()

  private var factm = Map.empty[QuizID, EntityRef[QuizFact.Command]]
  private def putFact(id: QuizID, beh: Behavior[QuizFact.Command]) =
    factm += (id -> TestEntityRef(QuizFact.EntityKey, id, testKit.spawn(beh)))
  private def putFact(id: QuizID, ref: ActorRef[QuizFact.Command]) =
    factm += (id -> TestEntityRef(QuizFact.EntityKey, id, ref))
  var nowTime = Instant.parse("2022-11-01T00:00:00Z")
  given (() => Instant) = () => nowTime

  val config = ExamConfig(preparationPeriodHours = 48, trialLengthMinutesRange = (5, 180))

  private val examKit = TestKit[Command, Event, Exam](
    testKit.system,
    ExamEntity(id, factm(_), () => tracker.ref, config)
  )

  val manualTime = ManualTime()(using testKit.system)

  override def beforeEach(): Unit =
    super.beforeEach()
    tracker = testKit.createTestProbe[ExamTracker.Command]()
    nowTime = Instant.parse("2022-11-01T00:00:00Z")
    examKit.clear()
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

  val dummyOutcome = TrialOutcome("", "", Instant.now(), Instant.now(), List.empty)

  "Exam entity" when {

    "Blank" must {

      "reject all commands except create" in {
        def rejected(cmds: (ActorRef[Resp[?]] => Command)*) = cmds.foreach { cmd =>
          val result = examKit.runCommand(cmd(_))
          result.reply shouldBe Bad(examNotFound.error())
          result.state shouldBe Blank()
        }

        rejected(IncludeTestees(Set.empty, _), ExcludeTestees(Set.empty, _), SetTrialAttrs(1, 2, _))

        val result = examKit.runCommand(Proceed)
        result.hasNoEvents shouldBe true
        result.state shouldBe Blank()
        val result2 = examKit.runCommand(Awake)
        result2.hasNoEvents shouldBe true
        result2.state shouldBe Blank()
        val result3 = examKit.runCommand(RegisterTrial(dummyOutcome))
        result3.hasNoEvents shouldBe true
        result3.state shouldBe Blank()
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
        tracker.expectNoMessage()
        val result = examKit
          .runCommand(Create(quiz.id, 60, period, Set(student1, student2, official3), official4, 70, _))
        result.reply shouldBe Good(CreateExamDetails(prepStart, official4))
        result.state shouldBe
          Pending(quiz, 60, prepStart, period, Set(student1, student2, official3), official4, 70)
        tracker.expectMessage(ExamTracker.Register(prepStart, period.start, "exam-1"))
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
        val result = examKit.runCommand(Create(quizID, 60, period, Set.empty, official1, 70, _))
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
        val result = examKit.runCommand(
          Create(
            "q",
            50,
            ExamPeriod(
              ZonedDateTime.parse("2022-11-12T10:00:00Z"),
              ZonedDateTime.parse("2022-11-10T10:00:00Z")
            ),
            Set.empty,
            official1,
            60,
            _
          )
        )
        result.reply shouldBe Bad(badExamPeriod.error())
        result.state shouldBe Blank()
      }

      "reject creation if period start too soon" in {
        val result = examKit.runCommand(
          Create(
            "q",
            50,
            ExamPeriod(
              ZonedDateTime.parse("2022-11-02T23:59:59Z"),
              ZonedDateTime.parse("2022-11-03T12:00:00Z")
            ),
            Set.empty,
            official1,
            60,
            _
          )
        )
        result.reply shouldBe Bad(examTooSoon.error() + "prep time hours" + "48")
        result.state shouldBe Blank()
      }

      "reject creation if trial length is not in range" in {
        val result1 = examKit.runCommand(Create("q", 4, period, Set.empty, official1, 60, _))
        result1.reply shouldBe Bad(badTrialLength.error() + "5" + "180")
        result1.state shouldBe Blank()

        val result2 = examKit.runCommand(Create("q", 181, period, Set.empty, official1, 60, _))
        result2.reply shouldBe Bad(badTrialLength.error() + "5" + "180")
        result2.state shouldBe Blank()
      }

      "reject creation if passing grade is not in range" in {
        val result1 = examKit.runCommand(Create("q", 90, period, Set.empty, official1, 10, _))
        result1.reply shouldBe Bad(badPassingGrade.error() + "50" + "100")
      }

    }

    val quiz = Quiz(UUID.randomUUID.toString, "test quiz")
    putFact(
      quiz.id,
      Behaviors.receiveMessage[QuizFact.Command] {
        case QuizFact.Use("exam-1", replyTo) =>
          replyTo ! Good(quiz)
          Behaviors.same
        case QuizFact.StopUse(_) =>
          Behaviors.same
        case _ =>
          Behaviors.stopped
      }
    )

    def init =
      val result = examKit.runCommand(Create(quiz.id, 60, period, Set.empty, official1, 70, _))
      val initial = Pending(quiz, 60, prepStart, period, Set.empty, official1, 70)
      result.state shouldBe initial
      initial

    "Pending" must {

      "include testees" in {
        val initial = init
        val result1 = examKit.runCommand(IncludeTestees(Set(student1, student2, official2), _))
        result1.reply shouldBe Good(Set(student1, student2, official2))
        result1.state shouldBe initial.copy(testees = Set(student1, student2, official2))
      }

      "exclude testees" in {
        val initial = init
        examKit.runCommand(IncludeTestees(Set(student1, student2, official2), _))
        val result = examKit.runCommand(ExcludeTestees(Set(student1, official2, student3), _))
        result.reply shouldBe Good(Set(student1, official2))
        result.state shouldBe initial.copy(testees = Set(student2))
      }

      "set trial attrs" in {
        val initial = init
        val result = examKit.runCommand(SetTrialAttrs(90, 85, _))
        result.reply shouldBe OK
        result.state shouldBe initial.copy(trialLengthMinutes = 90, passingGrade = 85)
      }

      "proceed to upcoming" in {
        val initial = init
        tracker.expectMessageType[ExamTracker.Register]
        val result = examKit.runCommand(Proceed)
        result.event shouldBe GoneUpcoming
        result.state shouldBe
          Upcoming(
            initial.quiz,
            initial.trialLengthMinutes,
            initial.period,
            initial.testees,
            initial.host,
            initial.passingGrade
          )
        tracker.expectMessage(ExamTracker.RegisterStateChange(id, State.Upcoming))
      }

      "be cancelled" in {
        val probe = testKit.createTestProbe[QuizFact.Command]()
        val qz = Quiz(UUID.randomUUID.toString, "tq")
        val mock = Behaviors.receiveMessage[QuizFact.Command] {
          case QuizFact.Use(_, replyTo) =>
            replyTo ! Good(qz)
            Behaviors.same
          case QuizFact.StopUse(_) =>
            Behaviors.same
          case _ =>
            Behaviors.stopped
        }
        putFact(qz.id, Behaviors.monitor(probe.ref, mock))
        val result0 = examKit.runCommand(Create(qz.id, 60, period, Set.empty, official1, 70, _))
        val initial = result0.stateOfType[Pending]
        val at = Instant.now()
        tracker.expectMessageType[ExamTracker.Register]
        val result = examKit.runCommand(Cancel(at, _))
        result.reply shouldBe OK
        result.state shouldBe
          Cancelled(
            initial.quiz,
            initial.trialLengthMinutes,
            initial.period,
            initial.testees,
            initial.host,
            initial.passingGrade,
            at
          )

        probe.expectMessageType[QuizFact.Use]
        probe.expectMessage(QuizFact.StopUse("exam-1"))
        tracker.expectMessage(ExamTracker.RegisterStateChange("exam-1", State.Cancelled))
      }

      "reject creation" in {
        val initial = init
        val result = examKit.runCommand(Create("", 1, period, Set.empty, official1, 2, _))
        result.reply shouldBe Bad(illegalState.error() + "Pending")
        result.state shouldBe initial
      }

      "reject register trial" in {
        init
        val result = examKit.runCommand(RegisterTrial(dummyOutcome))
        result.hasNoEvents shouldBe true
      }

      "be awaken and then go to upcoming" in {
        init
        nowTime = prepStart
          .toInstant
          .minus(config.awakeExamBeforeProceedMinutes, ChronoUnit.MINUTES)
        val result1 = examKit.runCommand(Awake)
        result1.hasNoEvents shouldBe true
        result1.state shouldBe an[Pending]
        manualTime.timePasses(config.awakeExamBeforeProceedMinutes.minutes)
        val result2 = examKit.runCommand(Create("", 1, period, Set.empty, official1, 2, _))
        result2.hasNoEvents shouldBe true
        result2.reply shouldBe Bad(illegalState.error() + "Upcoming")
      }

    }

    "Upcoming" must {

      "be cancelled" in {
        val initial = init
        val result0 = examKit.runCommand(Proceed)
        result0.state shouldBe
          Upcoming(
            initial.quiz,
            initial.trialLengthMinutes,
            initial.period,
            initial.testees,
            initial.host,
            initial.passingGrade
          )

        val at = Instant.now()
        tracker.receiveMessages(2)
        val result = examKit.runCommand(Cancel(at, _))
        result.reply shouldBe OK
        result.state shouldBe
          Cancelled(
            initial.quiz,
            initial.trialLengthMinutes,
            initial.period,
            initial.testees,
            initial.host,
            initial.passingGrade,
            at
          )
        tracker.expectMessage(ExamTracker.RegisterStateChange("exam-1", State.Cancelled))
      }

      "reject any other action" in {
        val initial = init
        examKit.runCommand(Proceed)
        def rejected(cmds: (ActorRef[Resp[?]] => Command)*) = cmds.foreach { cmd =>
          val result = examKit.runCommand(cmd(_))
          result.reply shouldBe Bad(illegalState.error() + "Upcoming")
        }

        rejected(
          Create("", 1, period, Set.empty, official1, 2, _),
          IncludeTestees(Set.empty, _),
          ExcludeTestees(Set.empty, _),
          SetTrialAttrs(2, 3, _)
        )
        val result = examKit.runCommand(RegisterTrial(dummyOutcome))
        result.hasNoEvents shouldBe true
      }

      "proceed to in progress" in {
        val pending = init
        examKit.runCommand(Proceed)
        tracker.receiveMessages(2)
        val result = examKit.runCommand(Proceed)
        result.event shouldBe GoneInProgress
        result.state shouldBe
          InProgress(
            quiz,
            pending.trialLengthMinutes,
            pending.period,
            pending.testees,
            pending.host,
            pending.passingGrade
          )
        tracker.expectMessage(ExamTracker.RegisterStateChange("exam-1", State.InProgress))
      }

      "be awaken and then go to 'in progress'" in {
        init
        examKit.runCommand(Proceed)
        nowTime = period
          .start
          .toInstant
          .minus(config.awakeExamBeforeProceedMinutes, ChronoUnit.MINUTES)
        val result1 = examKit.runCommand(Awake)
        result1.hasNoEvents shouldBe true
        result1.state shouldBe an[Upcoming]
        manualTime.timePasses(config.awakeExamBeforeProceedMinutes.minutes)
        val result2 = examKit.runCommand(Create("", 1, period, Set.empty, official1, 2, _))
        result2.hasNoEvents shouldBe true
        result2.reply shouldBe Bad(illegalState.error() + "InProgress")
      }

    }

    "InProgress" must {

      "reject any other action" in {
        val initial = init
        examKit.runCommand(Proceed)
        examKit.runCommand(Proceed)

        def rejected(cmds: (ActorRef[Resp[?]] => Command)*) = cmds.foreach { cmd =>
          val result = examKit.runCommand(cmd(_))
          result.reply shouldBe Bad(illegalState.error() + "InProgress")
        }

        rejected(
          Create("", 1, period, Set.empty, official1, 2, _),
          IncludeTestees(Set.empty, _),
          ExcludeTestees(Set.empty, _),
          SetTrialAttrs(2, 3, _),
          Cancel(Instant.now(), _)
        )
      }

      "register trial" in {
        init
        examKit.runCommand(IncludeTestees(Set(student1, student2), _))
        examKit.runCommand(Proceed)
        val state = examKit.runCommand(Proceed).stateOfType[InProgress]
        val outcome = TrialOutcome(
          student1.id,
          "trial1",
          period.start.toInstant.plus(10, ChronoUnit.MINUTES),
          period.start.toInstant.plus(20, ChronoUnit.MINUTES),
          List(Solution("sc1", "it1", List("1", "2")), Solution("sc1", "it2", List("2", "3")))
        )

        val result = examKit.runCommand(RegisterTrial(outcome))
        result.event shouldBe TrialRegistered(outcome)
        val newState = result.stateOfType[InProgress]
        newState.trials should contain only outcome
      }

      "not register trial if testee is not included in exam" in {
        init
        examKit.runCommand(Proceed)
        examKit.runCommand(Proceed)
        val outcome = TrialOutcome("notexist", "t", Instant.now(), Instant.now(), List.empty)
        val result = examKit.runCommand(RegisterTrial(outcome))
        result.hasNoEvents shouldBe true
      }

      "proceed to ended" in {
        val probe = testKit.createTestProbe[QuizFact.Command]()
        val qz = Quiz(UUID.randomUUID.toString, "tq")
        val mock = Behaviors.receiveMessage[QuizFact.Command] {
          case QuizFact.Use(_, replyTo) =>
            replyTo ! Good(qz)
            Behaviors.same
          case QuizFact.StopUse(_) =>
            Behaviors.same
          case _ =>
            Behaviors.stopped
        }
        putFact(qz.id, Behaviors.monitor(probe.ref, mock))
        val result0 = examKit.runCommand(Create(qz.id, 60, period, Set.empty, official1, 70, _))
        val pending = result0.stateOfType[Pending]
        examKit.runCommand(Proceed)
        examKit.runCommand(Proceed)
        val result = examKit.runCommand(Proceed)
        result.event shouldBe GoneEnded
        result.state shouldBe
          Ended(
            qz,
            pending.trialLengthMinutes,
            pending.period,
            pending.testees,
            pending.host,
            pending.passingGrade,
            Set.empty
          )
        probe.expectMessageType[QuizFact.Use]
        probe.expectMessage(QuizFact.StopUse("exam-1"))
      }

    }

    "Ended" must {
      "reject any action" in {
        val initial = init
        examKit.runCommand(Proceed)
        examKit.runCommand(Proceed)
        examKit.runCommand(Proceed)

        def rejected(cmds: (ActorRef[Resp[?]] => Command)*) = cmds.foreach { cmd =>
          val result = examKit.runCommand(cmd(_))
          result.reply shouldBe Bad(illegalState.error() + "Ended")
        }

        rejected(
          Create("", 1, period, Set.empty, official1, 2, _),
          IncludeTestees(Set.empty, _),
          ExcludeTestees(Set.empty, _),
          SetTrialAttrs(2, 3, _)
        )

        val result = examKit.runCommand(Proceed)
        result.hasNoEvents shouldBe true
        val result1 = examKit.runCommand(RegisterTrial(dummyOutcome))
        result1.hasNoEvents shouldBe true
      }
    }

    "Cancelled" must {
      "reject any action" in {
        val initial = init
        examKit.runCommand(Cancel(Instant.now(), _))

        def rejected(cmds: (ActorRef[Resp[?]] => Command)*) = cmds.foreach { cmd =>
          val result = examKit.runCommand(cmd(_))
          result.reply shouldBe Bad(illegalState.error() + "Cancelled")
        }

        rejected(
          Create("", 1, period, Set.empty, official1, 2, _),
          IncludeTestees(Set.empty, _),
          ExcludeTestees(Set.empty, _),
          SetTrialAttrs(2, 3, _)
        )

        val result = examKit.runCommand(Proceed)
        result.hasNoEvents shouldBe true
        val result2 = examKit.runCommand(RegisterTrial(dummyOutcome))
        result2.hasNoEvents shouldBe true
      }
    }

  }
