package quizzly.school

import java.time.*
import akka.actor.typed.*

final case class ExamPeriod(start: ZonedDateTime, end: ZonedDateTime)

sealed trait Exam:
  val id: ExamID

object Exam:

  sealed trait Command extends CborSerializable

  sealed trait Event extends CborSerializable

  final case class Blank(id: ExamID) extends Exam

  final case class Create(
      id: ExamID,
      quiz: QuizID,
      trialLengthMinutes: Int,
      period: ExamPeriod,
      testees: Set[PersonID],
      host: PersonID,
      replyTo: ActorRef[Resp[CreateExamDetails]]
  ) extends Command
  final case class CreateExamDetails(preparationStart: ZonedDateTime, host: Official)

  val examAlreadyExists = Reason(1001, "exam already exists")
  val badTrialLength = Reason(1002, "bad trial length")
  val badExamPeriod = Reason(1003, "bad exam period")
  val noTimeForPreparation = Reason(1004, "no time for preparation")
  val wrongQuiz = Reason(1005, "wrong quiz")
  val notOfficial = Reason(1006, "you are not an official")
  val examNotFound = Reason(1007, "exam not found")

  final case class Pending(
      id: ExamID,
      quiz: Quiz,
      trialLength: Duration,
      preparationStart: ZonedDateTime,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official
  ) extends Exam:
    def includeTestees(include: Set[Person]): Pending = Pending(
      id,
      quiz,
      trialLength,
      preparationStart,
      period,
      testees ++ include,
      host
    )
    def excludeTestees(exclude: Set[Person]): Pending = Pending(
      id,
      quiz,
      trialLength,
      preparationStart,
      period,
      testees -- exclude,
      host
    )
    def withTrialLength(length: Duration): Pending = Pending(
      id,
      quiz,
      length,
      preparationStart,
      period,
      testees,
      host
    )
    def proceed(): Upcoming = Upcoming(id, quiz, trialLength, period, testees, host)
    def cancel(at: Instant): Cancelled = Cancelled(id, quiz, trialLength, period, testees, host, at)

  final case class Created(
      id: ExamID,
      quiz: Quiz,
      trialLength: Duration,
      preparationStart: ZonedDateTime,
      period: ExamPeriod,
      testees: Set[PersonID],
      host: PersonID
  ) extends Event

  final case class IncludeTestees(include: Set[Person], replyTo: ActorRef[Resp[Set[Person]]])
      extends Command
  final case class TesteesIncluded(include: Set[Person]) extends Event

  final case class ExcludeTestees(exclude: Set[Person], replyTo: ActorRef[Resp[Set[Person]]])
      extends Command
  final case class TesteesExcluded(exclude: Set[Person]) extends Event

  final case class SetTrialLength(length: Duration, replyTo: ActorRef[Resp[Nothing]])
      extends Command
  final case class TrialLengthSet(length: Duration) extends Event

  case object Proceed extends Command

  final case class Upcoming(
      id: ExamID,
      quiz: Quiz,
      trialLength: Duration,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official
  ) extends Exam:
    def proceed(): InProgress = InProgress(id, quiz, trialLength, period, testees, host)
    def cancel(at: Instant): Cancelled = Cancelled(id, quiz, trialLength, period, testees, host, at)

  case object GoneUpcoming extends Event

  final case class InProgress(
      id: ExamID,
      quiz: Quiz,
      trialLength: Duration,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official
  ) extends Exam:
    def proceed(): Ended = Ended(id, quiz, trialLength, period, testees, host)

  case object GoneInProgress extends Event

  final case class Ended(
      id: ExamID,
      quiz: Quiz,
      trialLength: Duration,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official
  ) extends Exam

  final case class Cancelled(
      id: ExamID,
      quiz: Quiz,
      trialLength: Duration,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official,
      cancelledAt: Instant
  ) extends Exam
