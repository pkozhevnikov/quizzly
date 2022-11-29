package quizzly.school

import akka.actor.typed.{Behavior, ActorRef}
import akka.actor.typed.scaladsl.{Behaviors, ActorContext}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.*
import akka.cluster.sharding.typed.scaladsl.*
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Await}
import scala.util.{Success, Failure}

import java.time.temporal.ChronoUnit
import java.time.*

object ExamEntity:

  import Exam.*

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey("Exam")

  def apply(id: ExamID, facts: String => EntityRef[QuizFact.Command], config: ExamConfig)(using
      () => Instant,
      ExecutionContext
  ): Behavior[Command] = Behaviors.setup { ctx =>
    EventSourcedBehavior[Command, Event, Exam](
      PersistenceId.ofUniqueId(id),
      Blank(),
      commandHandler(ctx, id, facts, config),
      eventHandler
    )
  }

  given Timeout = 2.seconds

  import Resp.*

  def commandHandler(
      ctx: ActorContext[Command],
      id: ExamID,
      facts: String => EntityRef[QuizFact.Command],
      config: ExamConfig
  )(using now: () => Instant, ec: ExecutionContext): (Exam, Command) => Effect[Event, Exam] =
    (state, cmd) =>
      state match

        case _: Blank =>
          cmd match
            case c: Create =>
              val prepStart = c.period.start.minus(config.preparationPeriodHours, ChronoUnit.HOURS)
              if c.period.start.isAfter(c.period.end) then
                Effect.reply(c.replyTo)(Bad(badExamPeriod.error()))
              else if now().isAfter(prepStart.toInstant) then
                Effect.reply(c.replyTo)(
                  Bad(
                    examTooSoon.error() + "prep time hours" + config.preparationPeriodHours.toString
                  )
                )
              else if c.trialLengthMinutes < config.trialLengthMinutesRange(0) ||
                c.trialLengthMinutes > config.trialLengthMinutesRange(1)
              then
                Effect.reply(c.replyTo)(
                  Bad(
                    badTrialLength.error() + config.trialLengthMinutesRange(0).toString +
                      config.trialLengthMinutesRange(1).toString
                  )
                )
              else
                val extra = ctx.spawnAnonymous(
                  Behaviors.receiveMessage[Resp[Quiz]] {
                    case Good(quiz) =>
                      ctx.self !
                        InternalCreate(
                          quiz,
                          c.trialLengthMinutes,
                          prepStart,
                          c.period,
                          c.testees,
                          c.host,
                          c.replyTo
                        )
                      Behaviors.stopped
                    case Bad(e) =>
                      c.replyTo ! Bad(e)
                      Behaviors.stopped
                    case _ =>
                      Behaviors.stopped
                  }
                )
                facts(c.quizID) ! QuizFact.Use(id, extra)

                Effect.none

            case c: InternalCreate =>
              Effect
                .persist(
                  Created(
                    c.quiz,
                    c.trialLengthMinutes,
                    c.preparationStart,
                    c.period,
                    c.testees,
                    c.host
                  )
                )
                .thenReply(c.replyTo)(_ => Good(CreateExamDetails(c.preparationStart, c.host)))

            case c: CommandWithReply[_] =>
              Effect.reply(c.replyTo)(Bad(examNotFound.error()))

            case _ =>
              Effect.unhandled

        case pending: Pending =>
          cmd match
            case IncludeTestees(include, replyTo) =>
              Effect.persist(TesteesIncluded(include)).thenReply(replyTo)(_ => Resp.OK)
            case ExcludeTestees(exclude, replyTo) =>
              Effect.persist(TesteesExcluded(exclude)).thenReply(replyTo)(_ => Resp.OK)
            case SetTrialLength(length, replyTo) =>
              Effect.persist(TrialLengthSet(length)).thenReply(replyTo)(_ => Resp.OK)
            case Proceed =>
              Effect.persist(GoneUpcoming)
            case c: Create =>
              Effect.reply(c.replyTo)(Bad(illegalState.error() + "Pending"))
            case Cancel(at, replyTo) =>
              Effect.persist(GoneCancelled(at)).thenReply(replyTo)(_ => Resp.OK)
            case _ =>
              Effect.unhandled

        case upcoming: Upcoming =>
          cmd match
            case Cancel(at, replyTo) =>
              Effect.persist(GoneCancelled(at)).thenReply(replyTo)(_ => Resp.OK)
            case Proceed =>
              Effect.persist(GoneInProgress)
            case c: CommandWithReply[_] =>
              Effect.reply(c.replyTo)(Bad(illegalState.error() + "Upcoming"))
            case _ =>
              Effect.unhandled

        case inprogress: InProgress =>
          cmd match
            case Cancel(at, replyTo) =>
              Effect.persist(GoneCancelled(at)).thenReply(replyTo)(_ => Resp.OK)
            case Proceed =>
              Effect.persist(GoneEnded)
            case c: CommandWithReply[_] =>
              Effect.reply(c.replyTo)(Bad(illegalState.error() + "InProgress"))
            case _ =>
              Effect.unhandled

        case ended: Ended =>
          cmd match
            case c: CommandWithReply[_] =>
              Effect.reply(c.replyTo)(Bad(illegalState.error() + "Ended"))
            case _ =>
              Effect.unhandled

        case cancelled: Cancelled =>
          cmd match
            case c: CommandWithReply[_] =>
              Effect.reply(c.replyTo)(Bad(illegalState.error() + "Cancelled"))
            case _ =>
              Effect.unhandled

  import Resp.*

  val eventHandler: (Exam, Event) => Exam =
    (state, evt) =>
      state match

        case _: Blank =>
          evt match
            case c: Created =>
              Pending(c.quiz, c.trialLengthMinutes, c.preparationStart, c.period, c.testees, c.host)

        case pending: Pending =>
          evt match
            case TesteesIncluded(include) =>
              pending.copy(testees = pending.testees ++ include)
            case TesteesExcluded(exclude) =>
              pending.copy(testees = pending.testees -- exclude)
            case TrialLengthSet(length) =>
              pending.copy(trialLengthMinutes = length)
            case GoneUpcoming =>
              Upcoming(
                pending.quiz,
                pending.trialLengthMinutes,
                pending.period,
                pending.testees,
                pending.host
              )
            case GoneCancelled(at) =>
              Cancelled(
                pending.quiz,
                pending.trialLengthMinutes,
                pending.period,
                pending.testees,
                pending.host,
                at
              )
            case _ =>
              pending

        case upcoming: Upcoming =>
          evt match
            case GoneCancelled(at) =>
              Cancelled(
                upcoming.quiz,
                upcoming.trialLengthMinutes,
                upcoming.period,
                upcoming.testees,
                upcoming.host,
                at
              )
            case GoneInProgress =>
              InProgress(
                upcoming.quiz,
                upcoming.trialLengthMinutes,
                upcoming.period,
                upcoming.testees,
                upcoming.host
              )
            case _ =>
              upcoming

        case inprogress: InProgress =>
          evt match
            case GoneCancelled(at) =>
              Cancelled(
                inprogress.quiz,
                inprogress.trialLengthMinutes,
                inprogress.period,
                inprogress.testees,
                inprogress.host,
                at
              )
            case GoneEnded =>
              Ended(
                inprogress.quiz,
                inprogress.trialLengthMinutes,
                inprogress.period,
                inprogress.testees,
                inprogress.host
              )
            case _ =>
              inprogress

        case ended: Ended =>
          state

        case cancelled: Cancelled =>
          state
