package quizzly.trial

import akka.actor.typed.{Behavior, ActorRef}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.*
import akka.Done
import akka.pattern.StatusReply

import java.time.*
import java.time.temporal.*

import com.fasterxml.jackson.databind.annotation.*

object ExamEntity:

  type TimeSpan = (Instant, Instant)

  case class Exam(
      quiz: QuizID,
      period: ExamPeriod,
      availableWithin: TimeSpan,
      trialLength: Int,
      @JsonSerialize(keyUsing = classOf[PlainPersonSerializer])
      @JsonDeserialize(keyUsing = classOf[PlainPersonKeyDeserializer]) testees: Map[Person, Option[
        TrialID
      ]]
  ) extends CborSerializable

  case class ExamAttrs(id: ExamID, quiz: QuizID, start: Instant, end: Instant, trialLength: Int)

  sealed trait Command extends CborSerializable
  sealed trait CommandWithReply[R] extends Command:
    def replyTo: ActorRef[Resp[R]]
  sealed trait Event extends CborSerializable

  case class Register(quiz: QuizID, period: ExamPeriod, trialLength: Int, testees: Set[Person])
      extends Command
  case class Registered(quiz: QuizID, period: ExamPeriod, trialLength: Int, testees: Set[Person])
      extends Event
  case class RegisterTestee(trial: TrialID, testee: Person, replyTo: ActorRef[Resp[ExamAttrs]])
      extends CommandWithReply[ExamAttrs]
  case class TesteeRegistered(trial: TrialID, testee: Person) extends Event
  case object Unregister extends Command
  case object Unregistered extends Event
  case class GetInfo(replyTo: ActorRef[Resp[ExamAttrs]]) extends CommandWithReply[ExamAttrs]

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey("Exam")
  object Tags:
    val Single = "exam"
    val All = Vector("exam-1", "exam-2")

  import Resp.*

  def apply(id: ExamID)(using () => Instant) = EventSourcedBehavior[Command, Event, Option[Exam]](
    PersistenceId.of(EntityKey.name, id),
    None,
    handleCommand(id, _, _),
    handleEvent
  ).withTagger(_ => Set(Tags.Single))

  def handleCommand(id: ExamID, state: Option[Exam], command: Command)(using
      now: () => Instant
  ): Effect[Event, Option[Exam]] =
    state match
      case None =>
        command match
          case Register(quizId, period, trialLength, testees) =>
            Effect.persist(Registered(quizId, period, trialLength, testees))
          case c: CommandWithReply[?] =>
            Effect.reply(c.replyTo)(Bad(Trial.examNotFound.error()))
          case _ =>
            Effect.none

      case Some(exam) =>
        command match
          case GetInfo(replyTo) =>
            Effect.reply(replyTo)(
              Good(ExamAttrs(id, exam.quiz, exam.period.start, exam.period.end, exam.trialLength))
            )
          case RegisterTestee(trial, testee, replyTo) =>
            if !exam.testees.exists(_(0) == testee) then
              Effect.reply(replyTo)(Bad(Trial.notTestee.error()))
            else if exam.testees(testee).isDefined then
              Effect.reply(replyTo)(Bad(Trial.trialAlreadyStarted.error()))
            else if now().isAfter(exam.availableWithin(1)) then
              Effect.reply(replyTo)(Bad(Trial.examEnded.error()))
            else
              Effect
                .persist(TesteeRegistered(trial, testee))
                .thenReply(replyTo)(_ =>
                  Good(
                    ExamAttrs(
                      id,
                      exam.quiz,
                      exam.availableWithin(0),
                      exam.availableWithin(1),
                      exam.trialLength
                    )
                  )
                )
          case Unregister =>
            if !now().isAfter(exam.availableWithin(1)) then
              Effect.none
            else
              Effect.persist(Unregistered)
          case _ =>
            Effect.none

  def handleEvent(state: Option[Exam], event: Event): Option[Exam] =
    state match
      case None =>
        event match
          case Registered(quiz, period, trialLength, testees) =>
            Some(
              Exam(
                quiz,
                period,
                (period.start, period.end.minus(trialLength, ChronoUnit.MINUTES)),
                trialLength,
                testees.map((_, None)).toMap
              )
            )
          case _ =>
            None

      case Some(exam) =>
        event match
          case TesteeRegistered(trial, testee) =>
            Some(exam.copy(testees = exam.testees + (testee -> Some(trial))))
          case Unregistered =>
            None
          case _ =>
            state
