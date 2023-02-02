package quizzly.trial

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.testkit.typed.scaladsl.*
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.testkit.scaladsl.TestEntityRef

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.*

import java.time.*

import org.scalatest.*
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*

import com.typesafe.config.*

object TrialEntitySpec:
  val config: Config = ConfigFactory
    .parseString("""
      akka.actor {
        serialization-bindings {
          "quizzly.trial.CborSerializable" = jackson-cbor
        }
      }
      """)
    .withFallback(ManualTime.config)
    .withFallback(EventSourcedBehaviorTestKit.config)
    .resolve()

class TrialEntitySpec
    extends wordspec.AnyWordSpec,
      matchers.should.Matchers,
      BeforeAndAfterEach,
      BeforeAndAfterAll:

  val testKit = ActorTestKit(TrialEntitySpec.config)

  val id = "trial-1"

  import Trial.*
  import quizzly.school.{grpc => school}

  given ExecutionContext = testKit.system.executionContext

  var nowTime = Instant.parse("2023-01-30T10:00:00Z")
  given (() => Instant) = () => nowTime

  var exams = Map.empty[ExamID, EntityRef[ExamEntity.Command]]
  def putExam(id: ExamID, beh: Behavior[ExamEntity.Command]) =
    exams = exams + (id -> TestEntityRef(ExamEntity.EntityKey, id, testKit.spawn(beh)))

  override protected def beforeEach(): Unit =
    super.beforeEach()
    kit.clear()

  override protected def afterAll() = testKit.shutdownTestKit()

  val person1 = Person("pers1", "pers1 name")
  val person2 = Person("pers2", "pers2 name")
  val person3 = Person("pers3", "pers3 name")

  val section1 = Section(
    "q1-1",
    "section 1 title",
    "section 1 intro",
    List(
      Item("i1", "i1 intro", Statement("i1 def", None), List.empty, false, List.empty),
      Item("i2", "i2 intro", Statement("i2 def", None), List.empty, false, List.empty)
    )
  )

  val section2 = Section(
    "q1-2",
    "section 2 title",
    "section 2 intro",
    List(Item("i21", "i21 intro", Statement("i21 def", None), List.empty, false, List.empty))
  )

  val quiz1 = Quiz("q1", "q1 title", "q1 intro", List(section1, section2))

  val quizRegistry: QuizRegistry =
    new:
      def get(id: QuizID) =
        id match
          case "q1" =>
            Future.successful(quiz1)
          case _ =>
            Future.failed(java.util.NoSuchElementException(s"not found quiz [$id]"))

  val manualTime = ManualTime()(using testKit.system)

  val schoolRegistry = mock(classOf[school.SchoolRegistry])

  private val kit = EventSourcedBehaviorTestKit[Command, Event, Option[Trial]](
    testKit.system,
    TrialEntity(id, exams(_), quizRegistry, schoolRegistry)
  )

  import Resp.*

  "TrialEntity" when {

    "not started yet" should {

      "reject any command except start" in {
        val res1 = kit.runCommand(Submit(person1, "", List.empty, _))
        res1.hasNoEvents shouldBe true
        res1.reply shouldBe Bad(trialNotStarted.error())
      }

      "reject start if exam rejected" in {
        putExam(
          "e2",
          Behaviors.receiveMessage[ExamEntity.Command] {
            case ExamEntity.RegisterTestee(_, _, replyTo) =>
              replyTo ! Bad(examNotFound.error())
              Behaviors.stopped
            case _ =>
              Behaviors.stopped
          }
        )
        val result = kit.runCommand(Start(person1, "e2", _))
        result.hasNoEvents shouldBe true
        result.reply shouldBe Bad(examNotFound.error())
      }

      "be started" in {
        nowTime = Instant.parse("2023-01-29T10:00:00Z")
        putExam(
          "e1",
          Behaviors.receiveMessage[ExamEntity.Command] {
            case ExamEntity.RegisterTestee(trialId, pers, replyTo) =>
              replyTo !
                Good(
                  ExamEntity.ExamAttrs(
                    "e1",
                    "q1",
                    Instant.parse("2023-01-28T10:00:00Z"),
                    Instant.parse("2023-01-30T10:00:00Z"),
                    55
                  )
                )
              Behaviors.stopped
            case _ =>
              Behaviors.stopped
          }
        )
        val result = kit.runCommand(Start(person1, "e1", _))
        val started = Instant.parse("2023-01-29T10:00:00Z")
        result.event shouldBe Started(person1, "e1", quiz1, 55, started, "q1-1")
        result.state shouldBe
          Some(Trial(person1, "e1", quiz1, 55, started, None, "q1-1", Map.empty))
        result.reply shouldBe
          Good(StartTrialDetails("trial-1", person1, started, 55, quiz1.sections(0).view))
      }

    }

    val started = Instant.parse("2023-01-29T10:00:00Z")
    def start =
      nowTime = Instant.parse("2023-01-29T10:00:00Z")
      putExam(
        "e1",
        Behaviors.receiveMessage[ExamEntity.Command] {
          case ExamEntity.RegisterTestee(trialId, pers, replyTo) =>
            replyTo !
              Good(
                ExamEntity.ExamAttrs(
                  "e1",
                  "q1",
                  Instant.parse("2023-01-28T10:00:00Z"),
                  Instant.parse("2023-01-30T10:00:00Z"),
                  55
                )
              )
            Behaviors.stopped
          case _ =>
            Behaviors.stopped
        }
      )
      val result = kit.runCommand(Start(person1, "e1", _))
      result.event shouldBe Started(person1, "e1", quiz1, 55, started, "q1-1")
      result.state shouldBe Some(Trial(person1, "e1", quiz1, 55, started, None, "q1-1", Map.empty))
      result.reply shouldBe
        Good(StartTrialDetails("trial-1", person1, started, 55, quiz1.sections(0).view))

    "started" should {

      "reject start if trial is already started" in {
        start
        val result = kit.runCommand(Start(person1, "e1", _))
        result.hasNoEvents shouldBe true
        result.reply shouldBe Bad(trialAlreadyStarted.error())
      }

      "reject any command if trial is finalized" in {
        start
        val nt = Instant.parse("2023-01-29T10:10:00Z")
        nowTime = nt
        kit.runCommand(Submit(person1, "i1", List("1", "2"), _))
        kit.runCommand(Submit(person1, "i2", List("2", "3"), _))
        kit.runCommand(Submit(person1, "i21", List("1", "2"), _))
        val result2 = kit.runCommand(Submit(person1, "", List.empty, _))
        result2.hasNoEvents shouldBe true
        result2.reply shouldBe Bad(trialFinalized.error())
      }

      "reject any command if submitter is not a testee of the trial" in {
        start
        val result = kit.runCommand(Submit(person2, "", List.empty, _))
        result.hasNoEvents shouldBe true
        result.reply shouldBe Bad(notTestee.error())
      }

      "reject submit if item already submitted" in {
        start
        kit.runCommand(Submit(person1, "i1", List("1", "2"), _))
        val result = kit.runCommand(Submit(person1, "i1", List("1", "2"), _))
        result.hasNoEvents shouldBe true
        result.reply shouldBe Bad(itemAlreadySubmitted.error())
      }

      "reject submit if item not found" in {
        start
        val result = kit.runCommand(Submit(person1, "i3", List.empty, _))
        result.hasNoEvents shouldBe true
        result.reply shouldBe Bad(itemNotFound.error())
      }

      "submit items, switch section and finalize" in {
        start
        val nt = Instant.parse("2023-01-29T10:10:00Z")
        nowTime = nt
        val result = kit.runCommand(Submit(person1, "i1", List("1", "2"), _))
        result.events shouldBe Vector(Submitted("i1", List("1", "2")))
        result.state shouldBe
          Some(
            Trial(
              person1,
              "e1",
              quiz1,
              55,
              started,
              None,
              "q1-1",
              Map(("q1-1", "i1") -> List("1", "2"))
            )
          )
        result.reply shouldBe Good(SubmissionResult(None, false))
        val result2 = kit.runCommand(Submit(person1, "i2", List("3", "4"), _))
        result2.events shouldBe Vector(Submitted("i2", List("3", "4")), SectionSwitched("q1-2"))
        result2.state shouldBe
          Some(
            Trial(
              person1,
              "e1",
              quiz1,
              55,
              started,
              None,
              "q1-2",
              Map(("q1-1", "i1") -> List("1", "2"), ("q1-1", "i2") -> List("3", "4"))
            )
          )
        result2.reply shouldBe Good(SubmissionResult(Some(section2.view), false))

        reset(schoolRegistry)

        val result3 = kit.runCommand(Submit(person1, "i21", List("5", "6"), _))
        result3.events shouldBe Vector(Submitted("i21", List("5", "6")), Finalized(nt))
        val state = result3.stateOfType[Option[Trial]].get
        state.finalizedAt shouldBe Some(nt)
        state.solutions shouldBe
          Map(
            ("q1-1", "i1") -> List("1", "2"),
            ("q1-1", "i2") -> List("3", "4"),
            ("q1-2", "i21") -> List("5", "6")
          )
        result3.reply shouldBe Good(SubmissionResult(None, true))
        verify(schoolRegistry).registerTrialResults(
          school.RegisterTrialResultsRequest(
            "e1",
            "pers1",
            "trial-1",
            started.getEpochSecond,
            nt.getEpochSecond,
            Seq(
              school.Solution("q1-1", "i1", Seq("1", "2")),
              school.Solution("q1-1", "i2", Seq("3", "4")),
              school.Solution("q1-2", "i21", Seq("5", "6"))
            )
          )
        )
      }

      "finalize if not fully submitted within trial length" in {
        reset(schoolRegistry)
        start
        nowTime = Instant.parse("2023-01-29T10:00:00Z")
        manualTime.timePasses(54.minutes)
        val result1 = kit.runCommand(Submit(person1, "i1", List("1", "2"), _))
        val state1 = result1.stateOfType[Option[Trial]].get
        state1.finalizedAt shouldBe None
        state1.solutions shouldBe Map(("q1-1", "i1") -> List("1", "2"))
        result1.reply shouldBe Good(SubmissionResult(None, false))
        verify(schoolRegistry, never()).registerTrialResults(any())
        manualTime.timePasses(2.minutes)
        val result2 = kit.runCommand(Submit(person1, "i2", List("2", "3"), _))
        val state2 = result2.stateOfType[Option[Trial]].get
        state2.finalizedAt shouldBe Some(nowTime)
        state2.solutions shouldBe Map(("q1-1", "i1") -> List("1", "2"))
        result2.reply shouldBe Bad(trialFinalized.error())

        verify(schoolRegistry).registerTrialResults(
          school.RegisterTrialResultsRequest(
            "e1",
            "pers1",
            "trial-1",
            started.getEpochSecond,
            nowTime.getEpochSecond,
            Seq(school.Solution("q1-1", "i1", Seq("1", "2")))
          )
        )
      }

    }

  }
