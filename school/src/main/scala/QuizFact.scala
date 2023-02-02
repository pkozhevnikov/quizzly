package quizzly.school

import akka.actor.typed.{Behavior, ActorRef}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.*
import akka.Done
import akka.pattern.StatusReply

object QuizFact:

  sealed trait Command extends CborSerializable
  sealed trait Event extends CborSerializable

  sealed trait CommandWithReply[R] extends Command:
    val replyTo: ActorRef[Resp[R]]

  final case class Init(quiz: FullQuiz) extends Command
  final case class Inited(quiz: FullQuiz) extends Event

  case object SetObsolete extends Command
  case object GotObsolete extends Event

  final case class Publish(replyTo: ActorRef[RespOK]) extends CommandWithReply[Nothing]
  case object Published extends Event
  final case class Unpublish(replyTo: ActorRef[RespOK]) extends CommandWithReply[Nothing]
  case object Unpublished extends Event

  val notFound = Reason(1101, "quiz not found")
  val isObsolete = Reason(1102, "quiz is obsolete")
  val wasPublished = Reason(1103, "quiz had been published")
  val isNotPublished = Reason(1104, "quiz is not published")
  val isAlreadyUsed = Reason(1105, "quiz is already used by this exam")
  val isUsed = Reason(1106, "quiz is in use")

  final case class Use(examID: ExamID, replyTo: ActorRef[Resp[Quiz]]) extends CommandWithReply[Quiz]
  final case class Used(examID: ExamID) extends Event
  final case class StopUse(examID: ExamID) extends Command
  final case class UseStopped(examID: ExamID) extends Event
  case object GotUnused extends Event

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey("QuizFact")

  object Tags:
    val Single = "quizfact"
    val All = Vector("quizfact-1", "quizfact-2", "quizfact-3")

  def apply(id: QuizID, config: ExamConfig): Behavior[Command] =
    EventSourcedBehavior[Command, Event, Option[Fact]](
      PersistenceId.of(EntityKey.name, id),
      None,
      (state, command) =>
        state match
          case None =>
            command match
              case Init(quiz) =>
                Effect.persist(Inited(quiz))
              case c: CommandWithReply[?] =>
                Effect.reply(c.replyTo)(Resp.Bad(notFound.error()))
              case _ =>
                Effect.noReply
          case Some(fact) =>
            fact.takeCommand(id, command)
      ,
      (state, event) =>
        state match
          case None =>
            event match
              case Inited(quiz) =>
                Some(Fact(quiz, false, false, false, Set.empty))
              case _ =>
                throw IllegalStateException(s"current state is $state should be None")
          case Some(fact) =>
            Some(fact.takeEvent(event))
    ).withTagger(_ => Set(Tags.Single))

  import Resp.*

  final case class Fact(
      quiz: FullQuiz,
      obsolete: Boolean,
      everPublished: Boolean,
      isPublished: Boolean,
      usedBy: Set[ExamID]
  ) extends CborSerializable:

    private[QuizFact] def takeCommand(id: QuizID, command: Command): Effect[Event, Option[Fact]] =
      command match
        case SetObsolete =>
          Effect.persist(GotObsolete)
        case Publish(replyTo) =>
          if isPublished then
            Effect.reply(replyTo)(Bad(wasPublished.error()))
          else if !usedBy.isEmpty then
            Effect.reply(replyTo)(Bad(isUsed.error()))
          else
            Effect.persist(Published).thenReply(replyTo)(_ => Resp.OK)
        case Unpublish(replyTo) =>
          if !isPublished then
            Effect.reply(replyTo)(Bad(isNotPublished.error()))
          else
            Effect.persist(Unpublished).thenReply(replyTo)(_ => Resp.OK)
        case Use(examID, replyTo) =>
          if obsolete then
            Effect.reply(replyTo)(Bad(isObsolete.error()))
          else if usedBy(examID) then
            Effect.reply(replyTo)(Bad(isAlreadyUsed.error()))
          else if everPublished then
            Effect.reply(replyTo)(Bad(wasPublished.error()))
          else
            Effect.persist(Used(examID)).thenReply(replyTo)(_ => Good(Quiz(id, quiz.title)))

        case StopUse(examID) =>
          if usedBy(examID) then
            var events = Seq[Event](UseStopped(examID))
            if usedBy.size == 1 then
              events = events :+ GotUnused
            Effect.persist(events)
          else
            Effect.none
        case _: Init =>
          Effect.unhandled

    private[QuizFact] def takeEvent(event: Event) =
      event match
        case GotObsolete =>
          copy(obsolete = true)
        case Published =>
          copy(everPublished = true, isPublished = true)
        case Unpublished =>
          copy(isPublished = false)
        case Used(examID) =>
          copy(usedBy = usedBy + examID)
        case UseStopped(examID) =>
          copy(usedBy = usedBy - examID)
        case GotUnused =>
          this
        case _: Inited =>
          throw new IllegalStateException(s"current state is Fact should be None")
