package quizzly.author

import akka.actor.typed.Behavior
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.*

object QuizEntity:

  import Quiz.*

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey("Quiz")

  def apply(id: QuizID, config: QuizConfig): Behavior[Command] =
    EventSourcedBehavior[Command, Event, Quiz](
      PersistenceId.ofUniqueId(id),
      Blank(id),
      commandHandler(config),
      eventHandler
    )

  def commandHandler(config: QuizConfig): (Quiz, Command) => Effect[Event, Quiz] = (state, cmd) =>
    state match
      case _: Blank =>
        cmd match
          case Create(id, title, intro, curator, authors, inspectors, length, replyTo) =>
            val insps = inspectors - curator -- authors
            val auths = authors - curator
            (
              if length < config.minTrialLength then Some(tooShortLength)
              else if title.trim.length < config.minTitleLength then Some(tooShortTitle)
              else if insps.size < config.minInspectors then Some(notEnoughInspectors)
              else if auths.size < config.minAuthors then Some(notEnoughAuthors)
              else None
            ) match
              case Some(error) => Effect.reply(replyTo)(Bad(error))
              case _ =>
                Effect
                  .persist(Created(id, title, intro, curator, auths, insps, length))
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
