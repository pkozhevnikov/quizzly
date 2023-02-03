package quizzly.school

import akka.actor.typed.{Behavior, ActorRef, RecipientRef}
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

  def apply(
      id: ExamID,
      facts: String => EntityRef[QuizFact.Command],
      tracker: () => RecipientRef[ExamTracker.Command],
      config: ExamConfig
  )(using () => Instant, ExecutionContext): Behavior[Command] = Behaviors.setup { ctx =>
    Behaviors.withTimers { timers =>
      def scheduleProceeding(in: Long) = timers.startSingleTimer(Proceed, in.millis)
      EventSourcedBehavior[Command, Event, Exam](
        PersistenceId.of(EntityKey.name, id),
        Blank(),
        commandHandler(ctx, id, facts, tracker, config, scheduleProceeding),
        eventHandler
      ).withTagger(_ => Set(Tags.Single))
    }
  }

  given Timeout = 2.seconds

  import Resp.*

  def commandHandler(
      ctx: ActorContext[Command],
      id: ExamID,
      facts: String => EntityRef[QuizFact.Command],
      tracker: () => RecipientRef[ExamTracker.Command],
      config: ExamConfig,
      scheduleProceeding: Long => Unit
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
              else if c.passingGrade < config.passingGrade(0) || c.passingGrade > 100 then
                Effect.reply(c.replyTo)(Bad(badPassingGrade.error() + config.passingGrade(0).toString + "100"))
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
                          c.passingGrade,
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
                    c.host,
                    c.passingGrade
                  )
                )
                .thenRun((_: Exam) =>
                  tracker() ! ExamTracker.Register(c.preparationStart, c.period.start, id)
                )
                .thenReply(c.replyTo)(_ => Good(CreateExamDetails(c.preparationStart, c.host)))

            case c: CommandWithReply[?] =>
              Effect.reply(c.replyTo)(Bad(examNotFound.error()))

            case _ =>
              Effect.unhandled

        case pending: Pending =>
          cmd match
            case IncludeTestees(include, replyTo) =>
              val toadd = include &~ pending.testees
              if toadd.isEmpty then
                Effect.reply(replyTo)(Good(Set.empty[Person]))
              else
                Effect.persist(TesteesIncluded(toadd)).thenReply(replyTo)(_ => Good(toadd))
            case ExcludeTestees(exclude, replyTo) =>
              val toremove = pending.testees & exclude
              if toremove.isEmpty then
                Effect.reply(replyTo)(Good(Set.empty[Person]))
              else
                Effect.persist(TesteesExcluded(toremove)).thenReply(replyTo)(_ => Good(toremove))
            case SetTrialAttrs(length, grade, replyTo) =>
              if length < config.trialLengthMinutesRange(0) ||
                length > config.trialLengthMinutesRange(1)
              then
                Effect.reply(replyTo)(
                  Bad(
                    badTrialLength.error() + config.trialLengthMinutesRange(0).toString +
                      config.trialLengthMinutesRange(1).toString
                  )
                )
              else if grade < config.passingGrade(0) || grade > 100 then
                Effect.reply(replyTo)(Bad(badPassingGrade.error() + config.passingGrade(0).toString + "100"))
              else
                Effect.persist(TrialAttrsSet(length, grade))
                  .thenReply(replyTo)(_ => Resp.OK)
            case Proceed =>
              Effect
                .persist(GoneUpcoming)
                .thenRun(_ => tracker() ! ExamTracker.RegisterStateChange(id, State.Upcoming))
            case c: Create =>
              Effect.reply(c.replyTo)(Bad(illegalState.error() + "Pending"))
            case Cancel(at, replyTo) =>
              facts(pending.quiz.id) ! QuizFact.StopUse(id)
              Effect
                .persist(GoneCancelled(at))
                .thenRun((_: Exam) =>
                  tracker() ! ExamTracker.RegisterStateChange(id, Exam.State.Cancelled)
                )
                .thenReply(replyTo)(_ => Resp.OK)
            case Awake =>
              scheduleProceeding(now().until(pending.preparationStart, ChronoUnit.MILLIS))
              Effect.none
            case _ =>
              Effect.unhandled

        case upcoming: Upcoming =>
          cmd match
            case Cancel(at, replyTo) =>
              Effect
                .persist(GoneCancelled(at))
                .thenRun((_: Exam) =>
                  tracker() ! ExamTracker.RegisterStateChange(id, Exam.State.Cancelled)
                )
                .thenReply(replyTo)(_ => Resp.OK)
            case Proceed =>
              Effect
                .persist(GoneInProgress)
                .thenRun(_ => tracker() ! ExamTracker.RegisterStateChange(id, State.InProgress))
            case c: CommandWithReply[?] =>
              Effect.reply(c.replyTo)(Bad(illegalState.error() + "Upcoming"))
            case Awake =>
              scheduleProceeding(now().until(upcoming.period.start, ChronoUnit.MILLIS))
              Effect.none
            case _ =>
              Effect.unhandled

        case inprogress: InProgress =>
          cmd match
            case Proceed =>
              facts(inprogress.quiz.id) ! QuizFact.StopUse(id)
              Effect.persist(GoneEnded)
            case RegisterTrial(outcome) =>
              if inprogress.testees.exists(_.id == outcome.testeeId) then
                Effect.persist(TrialRegistered(outcome))
              else
                Effect.none
            case c: CommandWithReply[?] =>
              Effect.reply(c.replyTo)(Bad(illegalState.error() + "InProgress"))
            case _ =>
              Effect.unhandled

        case ended: Ended =>
          cmd match
            case c: CommandWithReply[?] =>
              Effect.reply(c.replyTo)(Bad(illegalState.error() + "Ended"))
            case _ =>
              Effect.unhandled

        case cancelled: Cancelled =>
          cmd match
            case c: CommandWithReply[?] =>
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
              Pending(c.quiz, c.trialLengthMinutes, c.preparationStart, c.period, c.testees, c.host, c.passingGrade)

        case pending: Pending =>
          evt match
            case TesteesIncluded(include) =>
              pending.copy(testees = pending.testees ++ include)
            case TesteesExcluded(exclude) =>
              pending.copy(testees = pending.testees -- exclude)
            case TrialAttrsSet(length, grade) =>
              pending.copy(trialLengthMinutes = length, passingGrade = grade)
            case GoneUpcoming =>
              Upcoming(
                pending.quiz,
                pending.trialLengthMinutes,
                pending.period,
                pending.testees,
                pending.host,
                pending.passingGrade
              )
            case GoneCancelled(at) =>
              Cancelled(
                pending.quiz,
                pending.trialLengthMinutes,
                pending.period,
                pending.testees,
                pending.host,
                pending.passingGrade,
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
                upcoming.passingGrade,
                at
              )
            case GoneInProgress =>
              InProgress(
                upcoming.quiz,
                upcoming.trialLengthMinutes,
                upcoming.period,
                upcoming.testees,
                upcoming.host,
                upcoming.passingGrade
              )
            case _ =>
              upcoming

        case inprogress: InProgress =>
          evt match
            case TrialRegistered(outcome) =>
              inprogress.copy(trials = inprogress.trials + outcome)
            case GoneEnded =>
              Ended(
                inprogress.quiz,
                inprogress.trialLengthMinutes,
                inprogress.period,
                inprogress.testees,
                inprogress.host,
                inprogress.passingGrade,
                inprogress.trials
              )
            case _ =>
              inprogress

        case ended: Ended =>
          state

        case cancelled: Cancelled =>
          state
