package quizzly.author

import akka.actor.typed.{Behavior, ActorRef}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.*

object SectionEditEntity:

  import SectionEdit.*
  import Resp.*

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey("SectionEdit")

  def apply(id: SC, config: QuizConfig): Behavior[Command] =
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
            takeCommand(edit, cmd)
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

  def takeCommand(edit: SectionEdit, cmd: Command): Effect[Event, Option[SectionEdit]] =
    Effect.unhandled

  def takeEvent(edit: SectionEdit, evt: Event): SectionEdit =
    evt match
      case Discharged =>
        edit.copy(owner = None)
      case Owned(owner) =>
        edit.copy(owner = Some(owner))

      case _ =>
        edit
