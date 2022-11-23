package quizzly.school

import akka.actor.typed.{Behavior, ActorRef}
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
  ): Behavior[Command] = EventSourcedBehavior[Command, Event, Exam](
    PersistenceId.ofUniqueId(id),
    Blank(),
    commandHandler(id, config, facts),
    eventHandler
  )

  given Timeout = 2.seconds

  import Resp.*

  def commandHandler(id: ExamID, config: ExamConfig, facts: String => EntityRef[QuizFact.Command])(
      using ExecutionContext
  ): (Exam, Command) => Effect[Event, Exam] =
    (state, cmd) =>
      state match

        case _: Blank =>
          cmd match
            case c: Create =>
              create(id, c, facts(c.quizID), config)

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

  private def create(id: ExamID, c: Create, fact: EntityRef[QuizFact.Command], config: ExamConfig)(
      using ExecutionContext
  ): Effect[Event, Exam] = 
    println("create proc started")
    Await.result(
      fact
        .ask(QuizFact.Use(id, _))
        .map { rsp =>
          println(s"got resp $rsp")
          rsp match
            case Good(quiz @ Quiz(_, _)) =>
              println(s"got quiz $quiz")
              val prepStart = c.period.start.minus(config.preparationPeriodHours, ChronoUnit.HOURS)
              Effect
                .persist(Created(quiz, c.trialLengthMinutes, prepStart, c.period, c.testees, c.host))
                .thenReply(c.replyTo)(_ => Good(CreateExamDetails(prepStart, c.host)))
            case Bad(Error(reason, clues)) =>
              Effect.reply(c.replyTo)(Bad(reason.error() ++ clues))
        },
      2.seconds
    )

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
