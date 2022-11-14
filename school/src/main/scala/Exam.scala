package quizzly.school

import java.time.*
import akka.actor.typed.*

type QuizID = String
type ExamID = String

final case class ExamPeriod(start: ZonedDateTime, end: ZonedDateTime)

final case class Quiz(id: QuizID, title: String)

sealed trait Exam:
  val id: ExamID

object Exam:

  case class Error(code: Int, reason: String, clues: Set[String] = Set.empty)
      extends CborSerializable:
    def +(clue: String): Error = Error(code, reason, clues + clue)
    def ++(clues: String*): Error = Error(code, reason, this.clues ++ clues)

  sealed trait Resp[+V] extends CborSerializable
  type RespOK = Resp[Nothing]
  object Resp:
    case object OK extends RespOK
  final case class Good[+V](v: V) extends Resp[V]
  final case class Bad[+V](e: Error) extends Resp[V]

  sealed trait Command[RV] extends CborSerializable:
    def replyTo: ActorRef[Resp[RV]]

  sealed trait Event extends CborSerializable

  final case class Blank(id: ExamID) extends Exam

  final case class Pending(
      id: ExamID,
      quiz: Quiz,
      trialLength: Duration,
      preparationStart: ZonedDateTime,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official
  ) extends Exam:
    def includeTestees(include: Set[Person]): Pending =
      Pending(id, quiz, trialLength, preparationStart, period, testees ++ include, host)
    def excludeTestees(exclude: Set[Person]): Pending =
      Pending(id, quiz, trialLength, preparationStart, period, testees -- exclude, host)
    def withTrialLength(length: Duration): Pending =
      Pending(id, quiz, length, preparationStart, period, testees, host)
    def upcoming(): Upcoming =
      Upcoming(id, quiz, trialLength, period, testees, host)
    def cancel(at: Instant): Cancelled =
      Cancelled(id, quiz, trialLength, period, testees, host, at)

  final case class CreateExam(
      id: ExamID, 
      quiz: QuizID, 
      trialLengthMinutes: Int,
      period: ExamPeriod,
      testees: Set[PersonID],
      host: PersonID,
      replyTo: ActorRef[Resp[CreateExamDetails]]
  ) extends Command[CreateExamDetails]
  final case class CreateExamDetails(preparationStart: ZonedDateTime, host: Official)
  final case class ExamCreated(
      quiz: Quiz, 
      trialLength: Duration, 
      preparationStart: ZonedDateTime,
      period: ExamPeriod,
      testees: Set[PersonID],
      host: PersonID
  ) extends Event
  def examAlreadyExists: Error = Error(1001, "exam already exists")
  def badTrialLength: Error = Error(1002, "bad trial length")
  def badExamPeriod: Error = Error(1003, "bad exam period")
  def noTimeForPreparation: Error = Error(1004, "no time for preparation")
  def wrongQuiz: Error = Error(1005, "wrong quiz")
  def notOfficial: Error = Error(1006, "you are not an official")

  final case class Upcoming(
      id: ExamID,
      quiz: Quiz,
      trialLength: Duration,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official
  ) extends Exam:
    def inProgress(): InProgress = ???
    def cancel(at: Instant): Cancelled =
      Cancelled(id, quiz, trialLength, period, testees, host, at)

  final case class InProgress(
      id: ExamID,
      quiz: Quiz,
      trialLength: Duration,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official
  ) extends Exam:
    def ended(): Ended = ???

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
