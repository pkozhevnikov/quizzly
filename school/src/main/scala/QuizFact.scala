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

  final case class Init(title: String, obsolete: Boolean) extends Command
  final case class Inited(title: String, obsolete: Boolean) extends Event

  case object SetObsolete extends Command
  case object GotObsolete extends Event

  final case class Publish(replyTo: ActorRef[RespOK]) extends CommandWithReply[Nothing]
  case object Published extends Event
  final case class Unpublish(replyTo: ActorRef[RespOK]) extends CommandWithReply[Nothing]
  case object Unpublished extends Event

  enum Reason(val code: Int, val reason: String) extends ErrorReason, CborSerializable:
    case notFound extends Reason(1101, "quiz not found")
    case isObsolete extends Reason(1102, "quiz is obsolete")
    case wasPublished extends Reason(1103, "quiz had been published")
    case isNotPublished extends Reason(1104, "quiz is not published")

  final case class Error(reason: ErrorReason, clues: Seq[String]):
    def +(clue: String) = Error(reason, clues :+ clue)

  trait ErrorReason:
    val code: Int
    val reason: String
    def error(): Error = Error(this, Seq.empty)

  enum Resp[+R] extends CborSerializable:
    case OK extends Resp[Nothing]
    case Good(value: R) extends Resp[R]
    case Bad(error: Error) extends Resp[Nothing]

  type RespOK = Resp[Nothing]

  // final case class MayBeUsed(examID: ExamID, replyTo: ActorRef[Boolean]) extends Command
  final case class Use(examID: ExamID, replyTo: ActorRef[RespOK]) extends CommandWithReply[Nothing]
  final case class Used(examID: ExamID) extends Event

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey("QuizFact")

  def apply(id: QuizID, config: ExamConfig): Behavior[Command] =
    EventSourcedBehavior[Command, Event, Option[Fact]](
      PersistenceId.ofUniqueId(id),
      None,
      (state, command) =>
        state match
          case None =>
            command match
              case Init(title, obsolete) =>
                Effect.persist(Inited(title, obsolete))
              case c: CommandWithReply[_] =>
                Effect.reply(c.replyTo)(Resp.Bad(Reason.notFound.error()))
              case _ =>
                Effect.noReply
          case Some(fact) =>
            fact.takeCommand(command)
      ,
      (state, event) =>
        state match
          case None =>
            event match
              case Inited(title, obsolete) =>
                Some(Fact(title, obsolete, false, false, Set.empty))
              case _ =>
                throw IllegalStateException(s"current state is $state should be None")
          case Some(fact) =>
            Some(fact.takeEvent(event))
    )

  final case class Fact(
      title: String,
      obsolete: Boolean,
      everPublished: Boolean,
      isPublished: Boolean,
      usedBy: Set[ExamID]
  ) extends CborSerializable:

    private[QuizFact] def takeCommand(command: Command): Effect[Event, Option[Fact]] =
      command match
        case SetObsolete =>
          Effect.persist(GotObsolete)
        case _: Publish =>
          Effect.persist(Published)
        case _: Unpublish =>
          Effect.persist(Unpublished)
        case Use(examID, replyTo) =>
          Effect.noReply
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
        case _: Inited =>
          throw new IllegalStateException(s"current state is Fact should be None")
