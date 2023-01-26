package quizzly.trial

import java.time.Instant
import akka.actor.typed.ActorRef

import com.fasterxml.jackson.databind.annotation.*

final case class StartTrialDetails(
    id: TrialID,
    testee: Person,
    startedAt: Instant,
    trialLength: Int,
    firstSection: SectionView
)

final case class SubmissionResult(nextSection: Option[SectionView], finalized: Boolean)

case class Trial(
    testee: Person,
    exam: ExamID,
    quiz: Quiz,
    length: Int,
    startedAt: Instant,
    finalizedAt: Option[Instant],
    currentSectionSC: SC,
    @JsonSerialize(keyUsing = classOf[StringPairSerializer])
    @JsonDeserialize(keyUsing = classOf[StringPairKeyDeserializer]) solutions: Map[(SC, SC), List[
      String
    ]] // key: (sectionSC, itemSC)
) extends CborSerializable:
  def currentSection = quiz.sections.find(_.sc == currentSectionSC).get
  def nextSection = quiz.sections.lift(quiz.sections.indexWhere(_.sc == currentSectionSC) + 1)

object Trial:

  sealed trait Command extends CborSerializable
  sealed trait CommandWithReply[R] extends Command:
    def replyTo: ActorRef[Resp[R]]

  sealed trait Event extends CborSerializable

  case class Start(testee: Person, exam: ExamID, replyTo: ActorRef[Resp[StartTrialDetails]])
      extends CommandWithReply[StartTrialDetails]
  val trialAlreadyStarted = Reason(3001, "trial already started")
  val trialFinalized = Reason(3002, "trial finalized")
  val notTestee = Reason(3003, "user is not a testee of this exam")
  val examEnded = Reason(3004, "exam ended")
  val examNotFound = Reason(3005, "exam not found")
  case class Started(
      testee: Person,
      exam: ExamID,
      quiz: Quiz,
      length: Int,
      at: Instant,
      sectionSC: SC
  ) extends Event

  case class Submit(
      testee: Person,
      itemSC: SC,
      solution: List[String],
      replyTo: ActorRef[Resp[SubmissionResult]]
  ) extends CommandWithReply[SubmissionResult]
  val itemNotFound = Reason(3006, "item not found")
  val trialNotStarted = Reason(3007, "trial not started")
  val itemAlreadySubmitted = Reason(3008, "item is already submitted")
  def processingError(ex: Throwable) = Reason(3100, ex.getMessage)
  case class Submitted(itemSC: SC, solution: List[String]) extends Event

  case class SectionSwitched(nextSC: SC) extends Event

  case object Finalize extends Command
  case class Finalized(at: Instant) extends Event

  case class InternalStart(
      attrs: ExamEntity.ExamAttrs,
      quiz: Quiz,
      testee: Person,
      replyTo: ActorRef[Resp[StartTrialDetails]]
  ) extends CommandWithReply[StartTrialDetails]
