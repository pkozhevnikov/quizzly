package quizzly.trial

import akka.actor.typed.{Behavior, ActorRef}
import akka.actor.typed.scaladsl.{Behaviors, ActorContext, TimerScheduler}
import akka.cluster.sharding.typed.scaladsl.{EntityTypeKey, EntityRef}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.*
import akka.Done
import akka.util.Timeout
import akka.pattern.StatusReply

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.*
import scala.util.{Success, Failure}

import java.time.*

trait QuizRegistry:
  def get(id: QuizID): Future[Quiz]

object TrialEntity:

  val log = org.slf4j.LoggerFactory.getLogger("TrialEntity")

  import Trial.*
  import Resp.*
  import quizzly.school.{grpc => school}

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey("Trial")

  object Tags:
    val Single = "trial"
    val All = Vector("trial-1", "trial-2")

  given Timeout = 2.seconds

  def apply(
      id: TrialID,
      exams: ExamID => EntityRef[ExamEntity.Command],
      quizRegistry: QuizRegistry,
      schoolRegistry: school.SchoolRegistry
  )(using now: () => Instant, ec: ExecutionContext) = Behaviors.setup[Command] { ctx =>
    Behaviors.withTimers { timers =>
      EventSourcedBehavior[Command, Event, Option[Trial]](
        PersistenceId.of(EntityKey.name, id),
        None,
        startHandler(id, ctx, exams, quizRegistry, schoolRegistry, timers, _, _),
        (state, event) =>
          state match
            case None =>
              event match
                case Started(testee, exam, quiz, length, at, sectionSC) =>
                  Some(Trial(testee, exam, quiz, length, at, None, sectionSC, Map.empty))
                case _ =>
                  throw IllegalStateException("trial not started yet")
            case Some(trial) =>
              takeEvent(trial, event)
      ).withTagger(_ => Set(Tags.Single))
    }
  }

  def startHandler(
      id: TrialID,
      ctx: ActorContext[Command],
      exams: ExamID => EntityRef[ExamEntity.Command],
      quizRegistry: QuizRegistry,
      schoolRegistry: school.SchoolRegistry,
      timers: TimerScheduler[Command],
      state: Option[Trial],
      command: Command
  )(using now: () => Instant, ec: ExecutionContext): Effect[Event, Option[Trial]] =
    state match
      case None =>
        command match
          case Start(person, examId, replyTo) =>
            val extra = ctx.spawnAnonymous(
              Behaviors.receiveMessage[Resp[ExamEntity.ExamAttrs]] {
                case Good(attrs) =>
                  quizRegistry
                    .get(attrs.quiz)
                    .onComplete {
                      case Success(quiz) =>
                        ctx.self ! InternalStart(attrs, quiz, person, replyTo)
                      case Failure(ex) =>
                        log.error("could not get quiz from registry", ex)
                    }
                  Behaviors.stopped
                case Bad(e) =>
                  replyTo ! Bad(e)
                  Behaviors.stopped
                case _ =>
                  Behaviors.stopped
              }
            )
            exams(examId) ! ExamEntity.RegisterTestee(id, person, extra)
            Effect.none
          case InternalStart(attrs, quiz, testee, replyTo) =>
            val at = now()
            timers.startSingleTimer(Finalize, attrs.trialLength.minutes)
            Effect
              .persist(Started(testee, attrs.id, quiz, attrs.trialLength, at, quiz.sections(0).sc))
              .thenReply(replyTo)(_ =>
                Good(StartTrialDetails(id, testee, at, attrs.trialLength, quiz.sections(0).view))
              )
          case c: CommandWithReply[?] =>
            Effect.reply(c.replyTo)(Bad(trialNotStarted.error()))
      case Some(trial) =>
        takeCommand(id, trial, command, schoolRegistry)

  def takeCommand(
      id: TrialID,
      state: Trial,
      command: Command,
      schoolRegistry: school.SchoolRegistry
  )(using now: () => Instant): Effect[Event, Option[Trial]] =
    command match
      case c: Start =>
        Effect.reply(c.replyTo)(Bad(trialAlreadyStarted.error()))
      case Submit(testee, itemSC, answers, replyTo) =>
        if testee != state.testee then
          Effect.reply(replyTo)(Bad(notTestee.error()))
        else if state.finalizedAt.isDefined then
          Effect.reply(replyTo)(Bad(trialFinalized.error()))
        else if !state.currentSection.items.exists(_.sc == itemSC) then
          Effect.reply(replyTo)(Bad(itemNotFound.error()))
        else if state.solutions.contains((state.currentSectionSC, itemSC)) then
          Effect.reply(replyTo)(Bad(itemAlreadySubmitted.error()))
        else
          var events: List[Event] = List(Submitted(itemSC, answers))
          val result =
            if state.currentSection.items.size ==
                state.solutions.filter(p => p(0)(0) == state.currentSectionSC).size + 1
            then
              state.nextSection match
                case Some(section) =>
                  events = events :+ SectionSwitched(section.sc)
                  SubmissionResult(Some(section.view), false)
                case None =>
                  events = events :+ Finalized(now())
                  SubmissionResult(None, true)
            else
              SubmissionResult(None, false)
          Effect
            .persist(events)
            .thenRun(checkToSendOutcome(_, id, schoolRegistry))
            .thenReply(replyTo)(_ => Good(result))
      case Finalize =>
        Effect
          .persist(Finalized(now()))
          .thenRun(checkToSendOutcome(_, id, schoolRegistry))

      case _ =>
        Effect.none

  private def checkToSendOutcome(
      state: Option[Trial],
      id: TrialID,
      schoolRegistry: school.SchoolRegistry
  ) =
    state match
      case Some(s) if s.finalizedAt.isDefined =>
        schoolRegistry.registerTrialResults(
          school.RegisterTrialResultsRequest(
            s.exam,
            s.testee.id,
            id,
            s.startedAt.getEpochSecond,
            s.finalizedAt.get.getEpochSecond,
            s.solutions.map((k, v) => school.Solution(k(0), k(1), v.toSeq)).toSeq
          )
        )
      case _ =>

  def takeEvent(state: Trial, event: Event): Option[Trial] =
    event match
      case Submitted(itemSC, answers) =>
        Some(
          state.copy(solutions = state.solutions + ((state.currentSectionSC, itemSC) -> answers))
        )
      case SectionSwitched(sc) =>
        Some(state.copy(currentSectionSC = sc))
      case Finalized(at) =>
        Some(state.copy(finalizedAt = Some(at)))
      case _ =>
        Some(state)
