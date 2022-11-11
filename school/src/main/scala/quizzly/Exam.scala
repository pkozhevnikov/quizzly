package quizzly

import java.time.*

type QuizID = String
type ExamID = String

final case class ExamPeriod(start: ZonedDateTime, end: ZonedDateTime)

final case class Quiz(id: QuizID, title: String)

sealed trait Exam:
  val id: ExamID

object Exam:

  final case class Blank(id: ExamID) extends Exam

  final case class Pending(
      id: ExamID,
      quiz: Quiz,
      trialLength: Duration,
      period: ExamPeriod,
      testees: Set[Person],
      host: Official
  ) extends Exam:
    def includeTestees(include: Set[Person]): Pending =
      Pending(id, quiz, trialLength, period, testees ++ include, host)
    def excludeTestees(exclude: Set[Person]): Pending =
      Pending(id, quiz, trialLength, period, testees -- exclude, host)
    def withTrialLength(length: Duration): Pending =
      Pending(id, quiz, length, period, testees, host)
    def upcoming(): Upcoming =
      Upcoming(id, quiz, trialLength, period, testees, host)
    def cancel(at: Instant): Cancelled =
      Cancelled(id, quiz, trialLength, period, testees, host, at)

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
