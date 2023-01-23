package quizzly.school

import akka.actor.typed.{Behavior, ActorRef, RecipientRef}
import akka.actor.typed.scaladsl.{Behaviors, ActorContext}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.*
import akka.cluster.sharding.typed.scaladsl.*
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.{Success, Failure}

import java.time.temporal.ChronoUnit
import java.time.*

object ExamTracker:

  sealed trait Command extends CborSerializable
  sealed trait Event extends CborSerializable

  case class Register(prestartAt: ZonedDateTime, startAt: ZonedDateTime, id: String) extends Command
  case class Created(prestartAt: ZonedDateTime, startAt: ZonedDateTime, id: String) extends Event
  case class RegisterStateChange(id: String, state: Exam.State) extends Command
  case class StateChanged(id: String, state: Exam.State) extends Event
  case object Check extends Command

  case class ExamRef(id: String, prestartAt: Instant, startAt: Instant, state: Exam.State)
      extends CborSerializable
  case class Tracked(all: Set[ExamRef]) extends CborSerializable

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey("ExamTracker")

  object Tags:
    val Single = "examtracker"

  val ID = "examtracker"

  def prevState(forState: Exam.State) =
    if forState == Exam.State.InProgress then
      Exam.State.Upcoming
    else if forState == Exam.State.Upcoming then
      Exam.State.Pending
    else
      throw IllegalArgumentException(s"unexpected state $forState")

  def apply(config: ExamConfig, examsAware: String => RecipientRef[Exam.Command])(
      using () => Instant
  ): Behavior[Command] = Behaviors.withTimers { timers =>
    timers.startTimerAtFixedRate(Check, FiniteDuration(config.trackerCheckRateMinutes, MINUTES))
    EventSourcedBehavior[Command, Event, Tracked](
      PersistenceId.of(EntityKey.name, ID),
      Tracked(Set.empty),
      (state, cmd) => handleCommand(state, cmd, config, examsAware),
      processEvent
    ).withTagger(_ => Set(Tags.Single))
  }

  def handleCommand(
      state: Tracked,
      cmd: Command,
      config: ExamConfig,
      examsAware: String => RecipientRef[Exam.Command]
  )(using now: () => Instant): Effect[Event, Tracked] =
    cmd match
      case Register(prestart, start, id) =>
        if !state.all.exists(_.id == id) then
          Effect.persist(Created(prestart, start, id))
        else
          Effect.none
      case RegisterStateChange(id, Exam.State.Cancelled) =>
        if state.all.exists(_.id == id) then
          Effect.persist(StateChanged(id, Exam.State.Cancelled))
        else
          Effect.none
      case RegisterStateChange(id, newState) =>
        if state.all.exists(ref => ref.id == id && ref.state == prevState(newState)) then
          Effect.persist(StateChanged(id, newState))
        else
          Effect.none

      case Check =>
        val currentTime = now()
        state
          .all
          .foreach { ref =>
            val prestart = ref
              .prestartAt
              .minus(config.awakeExamBeforeProceedMinutes, ChronoUnit.MINUTES)
            val start = ref.startAt.minus(config.awakeExamBeforeProceedMinutes, ChronoUnit.MINUTES)
            if (currentTime.equals(prestart) || currentTime.isAfter(prestart)) ||
              (currentTime.equals(start) || currentTime.isAfter(start))
            then
              examsAware(ref.id) ! Exam.Awake
          }
        Effect.none

  def processEvent(state: Tracked, evt: Event) =
    evt match
      case Created(prestart, start, id) =>
        Tracked(state.all + ExamRef(id, prestart.toInstant, start.toInstant, Exam.State.Pending))
      case StateChanged(id, Exam.State.InProgress | Exam.State.Cancelled) =>
        Tracked(state.all.filterNot(_.id == id))
      case StateChanged(id, newState) =>
        Tracked(
          state
            .all
            .map { ref =>
              if ref.id == id then
                ref.copy(state = newState)
              else
                ref
            }
        )
