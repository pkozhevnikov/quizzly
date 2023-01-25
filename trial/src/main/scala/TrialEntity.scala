package quizzly.trial

import akka.actor.typed.{Behavior, ActorRef}
import akka.actor.typed.scaladsl.{Behaviors, ActorContext}
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

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey("Trial")

  object Tags:
    val Single = "trial"
    val All = Vector("trial-1", "trial-2")

  given Timeout = 2.seconds

  def apply(
      id: TrialID,
      exams: ExamID => EntityRef[ExamEntity.Command],
      quizRegistry: QuizRegistry
  )(using now: () => Instant, ec: ExecutionContext) = Behaviors.setup[Command] { ctx =>
    EventSourcedBehavior[Command, Event, Option[Trial]](
      PersistenceId.of(EntityKey.name, id),
      None,
      startHandler(id, ctx, exams, quizRegistry, _, _),
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

  def startHandler(
      id: ExamID,
      ctx: ActorContext[Command],
      exams: ExamID => EntityRef[ExamEntity.Command],
      quizRegistry: QuizRegistry,
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
            Effect
              .persist(Started(testee, attrs.id, quiz, attrs.trialLength, at, quiz.sections(0).sc))
              .thenReply(replyTo)(_ =>
                Good(StartTrialDetails(id, testee, at, attrs.trialLength, quiz.sections(0).view))
              )
          case c: CommandWithReply[?] =>
            Effect.reply(c.replyTo)(Bad(trialNotStarted.error()))
      case Some(trial) =>
        takeCommand(trial, command)

  def takeCommand(state: Trial, command: Command): Effect[Event, Option[Trial]] = ???

  def takeEvent(state: Trial, event: Event): Option[Trial] = ???
