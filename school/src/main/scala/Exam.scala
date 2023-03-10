package quizzly.school

import java.time.*
import akka.actor.typed.*

final case class ExamPeriod(start: ZonedDateTime, end: ZonedDateTime) extends CborSerializable
final case class Solution(sectionSc: SC, itemSc: SC, answers: List[String]) extends CborSerializable
case class TrialOutcome(
    testeeId: PersonID,
    trialId: TrialID,
    start: Instant,
    end: Instant,
    solutions: List[Solution]
) extends CborSerializable:
  def withScore(score: Int) = GradedTrialOutcome(testeeId, trialId, start, end, solutions, score)
final case class GradedTrialOutcome(
    testeeId: PersonID,
    trialId: TrialID,
    start: Instant,
    end: Instant,
    solutions: List[Solution],
    score: Int
) extends CborSerializable
  

sealed trait Exam extends CborSerializable

object Exam:

  sealed trait Command extends CborSerializable
  sealed trait CommandWithReply[R] extends Command:
    val replyTo: ActorRef[Resp[R]]

  sealed trait Event extends CborSerializable

  object Tags:
    val Single = "exams"
    val All = Vector("exam-1", "exam-2", "exam-3")

  enum State:
    case Pending,
      Upcoming,
      InProgress,
      Ended,
      Cancelled

  final case class Blank() extends Exam

  final case class Create(
      quizID: QuizID,
      trialLengthMinutes: Int,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official,
      passingGrade: Int,
      replyTo: ActorRef[Resp[CreateExamDetails]]
  ) extends CommandWithReply[CreateExamDetails]
  final case class CreateExamDetails(preparationStart: ZonedDateTime, host: Official)

  val examAlreadyExists = Reason(1001, "exam already exists")
  val badTrialLength = Reason(1002, "bad trial length")
  val badExamPeriod = Reason(1003, "bad exam period")
  val examTooSoon = Reason(1004, "exam too soon")
  val wrongQuiz = Reason(1005, "wrong quiz")
  val notOfficial = Reason(1006, "you are not an official")
  val examNotFound = Reason(1007, "exam not found")
  val illegalState = Reason(1008, "illegal state")
  val badPassingGrade = Reason(1009, "bad passing grade")
  def unprocessed(msg: String) = Reason(5000, msg)

  final case class Pending(
      quiz: Quiz,
      trialLengthMinutes: Int,
      preparationStart: ZonedDateTime,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official,
      passingGrade: Int
  ) extends Exam

  final case class InternalCreate(
      quiz: Quiz,
      trialLengthMinutes: Int,
      preparationStart: ZonedDateTime,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official,
      passingGrade: Int,
      replyTo: ActorRef[Resp[CreateExamDetails]]
  ) extends Command
  final case class Created(
      quiz: Quiz,
      trialLengthMinutes: Int,
      preparationStart: ZonedDateTime,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official,
      passingGrade: Int
  ) extends Event

  final case class IncludeTestees(include: Set[Person], replyTo: ActorRef[Resp[Set[Person]]])
      extends CommandWithReply[Set[Person]]
  final case class TesteesIncluded(include: Set[Person]) extends Event

  final case class ExcludeTestees(exclude: Set[Person], replyTo: ActorRef[Resp[Set[Person]]])
      extends CommandWithReply[Set[Person]]
  final case class TesteesExcluded(exclude: Set[Person]) extends Event

  final case class SetTrialAttrs(length: Int, passingGrade: Int, replyTo: ActorRef[RespOK])
      extends CommandWithReply[Nothing]
  final case class TrialAttrsSet(length: Int, passingGrade: Int) extends Event

  case object Awake extends Command
  case object Proceed extends Command

  final case class Upcoming(
      quiz: Quiz,
      trialLengthMinutes: Int,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official,
      passingGrade: Int
  ) extends Exam

  case object GoneUpcoming extends Event

  final case class InProgress(
      quiz: Quiz,
      trialLengthMinutes: Int,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official,
      passingGrade: Int,
      trials: Set[GradedTrialOutcome] = Set.empty
  ) extends Exam

  final case class RegisterTrial(outcome: TrialOutcome) extends Command
  final case class InternalRegisterTrial(outcome: GradedTrialOutcome) extends Command
  final case class TrialRegistered(outcome: GradedTrialOutcome) extends Event

  case object GoneInProgress extends Event

  case object GoneEnded extends Event
  final case class Ended(
      quiz: Quiz,
      trialLengthMinutes: Int,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official,
      passingGrade: Int,
      trials: Set[GradedTrialOutcome]
  ) extends Exam

  final case class Cancel(at: Instant, replyTo: ActorRef[RespOK]) extends CommandWithReply[Nothing]
  final case class GoneCancelled(at: Instant) extends Event
  final case class Cancelled(
      quiz: Quiz,
      trialLengthMinutes: Int,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official,
      passingGrade: Int,
      cancelledAt: Instant
  ) extends Exam
