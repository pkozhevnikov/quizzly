package quizzly.author

import akka.actor.typed.Behavior
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.*

object QuizEntity:

  import Quiz.*

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey("Quiz")

  def apply(id: QuizID): Behavior[Command] = EventSourcedBehavior[Command, Event, Quiz](
    PersistenceId.ofUniqueId(id),
    Blank(id),
    commandHandler,
    eventHandler
  )

  def commandHandler: (Quiz, Command) => Effect[Event, Quiz] = (state, cmd) =>
    state match
      case _: Blank =>
        cmd match
          case Create(id, title, intro, curator, authors, inspectors, length, replyTo) =>
            Effect
              .persist(Created(id, title, intro, curator, authors, inspectors, length))
              .thenReply(replyTo) { case s: Composing =>
                Good(
                  CreateDetails(s.authors, s.inspectors)
                )
              }
          case _ => Effect.unhandled
      case _ => Effect.unhandled

  val eventHandler: (Quiz, Event) => Quiz = (state, evt) =>
    state match
      case _: Blank =>
        evt match
          case Created(id, title, intro, curator, authors, inspectors, length) =>
            Composing(id, title, intro, curator, authors, inspectors, length)
