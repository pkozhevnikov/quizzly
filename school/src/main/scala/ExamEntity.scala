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

object ExamEntity:

  import Exam.*

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey("Exam")

  def apply(id: ExamID, config: ExamConfig)(using
      facts: String => EntityRef[QuizFact.Command],
      ec: ExecutionContext
  ): Behavior[Command] = Behaviors.setup { ctx =>
    // given ActorContext[Command] = ctx
    EventSourcedBehavior[Command, Event, Exam](
      PersistenceId.ofUniqueId(id),
      Blank(),
      commandHandler(ctx, id, config, facts),
      eventHandler
    )
  }

  given Timeout = 2.seconds

  import Resp.*

  def commandHandler(
      ctx: ActorContext[Command],
      id: ExamID,
      config: ExamConfig,
      facts: String => EntityRef[QuizFact.Command]
  )(using ExecutionContext): (Exam, Command) => Effect[Event, Exam] =
    (state, cmd) =>
      state match

        case _: Blank =>
          cmd match
            case c: Create =>
              val extra = ctx.spawnAnonymous(
                Behaviors.receiveMessage[Resp[Quiz]] {
                  case Good(quiz) =>
                    ctx.self !
                      InternalCreate(
                        quiz,
                        c.trialLengthMinutes,
                        c.period.start.minus(config.preparationPeriodHours, ChronoUnit.HOURS),
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
          Effect.none

        case ended: Ended =>
          Effect.none

        case cancelled: Cancelled =>
          Effect.none

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
          state

        case ended: Ended =>
          state

        case cancelled: Cancelled =>
          state
