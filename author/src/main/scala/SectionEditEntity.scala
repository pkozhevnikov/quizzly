package quizzly.author

import akka.actor.typed.{Behavior, ActorRef}
import akka.actor.typed.scaladsl.{Behaviors, ActorContext}
import akka.cluster.sharding.typed.scaladsl.{EntityTypeKey, EntityRef}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.*

import scala.concurrent.ExecutionContext

object SectionEditEntity:

  import SectionEdit.*
  import Resp.*

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey("SectionEdit")

  def apply(id: SC, quizzes: String => EntityRef[Quiz.Command], config: QuizConfig)(using
      ExecutionContext
  ): Behavior[Command] = Behaviors.setup { ctx =>
    EventSourcedBehavior[Command, Event, Option[SectionEdit]](
      PersistenceId.ofUniqueId(id),
      None,
      (state, cmd) =>
        state match
          case None =>
            cmd match
              case Create(title, owner, quizID, replyTo) =>
                Effect.persist(Created(title, owner, quizID)).thenReply(replyTo)(_ => Resp.OK)
              case c: CommandWithReply[_] =>
                Effect.reply(c.replyTo)(Bad(Quiz.sectionNotFound.error()))
              case _ =>
                Effect.unhandled

          case Some(edit) =>
            cmd match
              case c: Create =>
                Effect.reply(c.replyTo)(Bad(alreadyExists.error()))
              case c: CommandWithOwnerReply[_] =>
                edit.owner match
                  case Some(person) =>
                    if c.owner != person then
                      Effect.reply(c.replyTo)(Bad(notOwner.error()))
                    else
                      takeCommand(edit, cmd, quizzes, ctx)
                  case _ =>
                    takeCommand(edit, cmd, quizzes, ctx)
              case _ => takeCommand(edit, cmd, quizzes, ctx)
      ,
      (state, evt) =>
        state match
          case None =>
            evt match
              case Created(title, owner, quizID) =>
                Some(SectionEdit(Some(owner), Section(id, title, List.empty), quizID))
              case _ =>
                state
          case Some(edit) =>
            Some(takeEvent(edit, evt))
    )
  }

  def takeCommand(
      edit: SectionEdit,
      cmd: Command,
      quizzes: String => EntityRef[Quiz.Command],
      ctx: ActorContext[Command]
  )(using ExecutionContext): Effect[Event, Option[SectionEdit]] = cmd match
    case Update(_, title, replyTo) =>
      Effect.persist(Updated(title)).thenReply(replyTo)(_ => Resp.OK)
    case Own(author, replyTo) => 
      edit.owner match
        case None =>
          Effect.persist(Owned(author)).thenReply(replyTo)(_ => Resp.OK)
        case Some(owner) =>
          Effect.reply(replyTo)(Bad(alreadyOwned.error() + owner.name))

  def takeEvent(edit: SectionEdit, evt: Event): SectionEdit =
    evt match
      case Updated(title) =>
        edit.copy(section = edit.section.copy(title = title))
      case Discharged =>
        edit.copy(owner = None)
      case Owned(owner) =>
        edit.copy(owner = Some(owner))

      case _ =>
        edit
