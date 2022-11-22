package quizzly.school

import java.time.*
import akka.actor.typed.*

final case class ExamPeriod(start: ZonedDateTime, end: ZonedDateTime)

sealed trait Exam

object Exam:

  sealed trait Command extends CborSerializable
  sealed trait CommandWithReply[R] extends Command:
    val replyTo: ActorRef[Resp[R]]

  sealed trait Event extends CborSerializable

  final case class Blank() extends Exam

  final case class Create(
      quizID: QuizID,
      trialLengthMinutes: Int,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official,
      replyTo: ActorRef[Resp[CreateExamDetails]]
  ) extends CommandWithReply[CreateExamDetails]
  final case class CreateExamDetails(preparationStart: ZonedDateTime, host: Official)

  val examAlreadyExists = Reason(1001, "exam already exists")
  val badTrialLength = Reason(1002, "bad trial length")
  val badExamPeriod = Reason(1003, "bad exam period")
  val noTimeForPreparation = Reason(1004, "no time for preparation")
  val wrongQuiz = Reason(1005, "wrong quiz")
  val notOfficial = Reason(1006, "you are not an official")
  val examNotFound = Reason(1007, "exam not found")
  def unprocessed(msg: String) = Reason(5000, msg)

  final case class Pending(
      quiz: Quiz,
      trialLengthMinutes: Int,
      preparationStart: ZonedDateTime,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official
  ) extends Exam:
    def includeTestees(include: Set[Person]): Pending = Pending(
      quiz,
      trialLengthMinutes,
      preparationStart,
      period,
      testees ++ include,
      host
    )
    def excludeTestees(exclude: Set[Person]): Pending = Pending(
      quiz,
      trialLengthMinutes,
      preparationStart,
      period,
      testees -- exclude,
      host
    )
    def withTrialLength(length: Int): Pending = Pending(
      quiz,
      length,
      preparationStart,
      period,
      testees,
      host
    )
    def proceed(): Upcoming = Upcoming(quiz, trialLengthMinutes, period, testees, host)
    def cancel(at: Instant): Cancelled = Cancelled(
      quiz,
      trialLengthMinutes,
      period,
      testees,
      host,
      at
    )

  final case class Created(
      quiz: Quiz,
      trialLengthMinutes: Int,
      preparationStart: ZonedDateTime,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official
  ) extends Event

  final case class IncludeTestees(include: Set[Person], replyTo: ActorRef[Resp[Set[Person]]])
      extends CommandWithReply[Set[Person]]
  final case class TesteesIncluded(include: Set[Person]) extends Event

  final case class ExcludeTestees(exclude: Set[Person], replyTo: ActorRef[Resp[Set[Person]]])
      extends CommandWithReply[Set[Person]]
  final case class TesteesExcluded(exclude: Set[Person]) extends Event

  final case class SetTrialLength(length: Int, replyTo: ActorRef[RespOK])
      extends CommandWithReply[Nothing]
  final case class TrialLengthSet(length: Int) extends Event

  case object Proceed extends Command

  final case class Upcoming(
      quiz: Quiz,
      trialLengthMinutes: Int,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official
  ) extends Exam:
    def proceed(): InProgress = InProgress(quiz, trialLengthMinutes, period, testees, host)
    def cancel(at: Instant): Cancelled = Cancelled(
      quiz,
      trialLengthMinutes,
      period,
      testees,
      host,
      at
    )

  case object GoneUpcoming extends Event

  final case class InProgress(
      quiz: Quiz,
      trialLengthMinutes: Int,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official
  ) extends Exam:
    def proceed(): Ended = Ended(quiz, trialLengthMinutes, period, testees, host)

  case object GoneInProgress extends Event

  final case class Ended(
      quiz: Quiz,
      trialLengthMinutes: Int,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official
  ) extends Exam

  final case class Cancelled(
      quiz: Quiz,
      trialLengthMinutes: Int,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official,
      cancelledAt: Instant
  ) extends Exam
