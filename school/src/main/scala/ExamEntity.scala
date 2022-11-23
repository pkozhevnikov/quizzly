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
          Effect.none

        case upcoming: Upcoming =>
          Effect.none

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
          state

        case upcoming: Upcoming =>
          state

        case inprogress: InProgress =>
          state

        case ended: Ended =>
          state

        case cancelled: Cancelled =>
          state
