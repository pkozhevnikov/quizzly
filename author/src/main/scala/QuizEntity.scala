package quizzly.author

import akka.actor.typed.{Behavior, ActorRef}
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
            val titl = title.trim
            (
              if length < config.minTrialLength then Some(tooShortLength)
              else if titl.length < config.minTitleLength then Some(tooShortTitle)
              else if insps.size < config.minInspectors then Some(notEnoughInspectors)
              else if auths.size < config.minAuthors then Some(notEnoughAuthors)
              else None
            ) match
              case Some(error) => Effect.reply(replyTo)(Bad(error))
              case _ =>
                Effect
                  .persist(Created(id, titl, intro, curator, auths, insps, length))
                  .thenReply(replyTo) { case s: Composing =>
                    Good(
                      CreateDetails(s.authors, s.inspectors)
                    )
                  }
          case c: Update => Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
          case c: AddInspector => Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
          case c: AddAuthor => Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
          case c: RemoveInspector => Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
          case c: RemoveAuthor => Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
          case c: AddSection => Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
          case c: SetObsolete => Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
          case c: GrabSection => Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
          case c: DischargeSection => Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
          case c: MoveSection => Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
          case c: RemoveSection => Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
          case c: SetReadySign => Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
          case c: Resolve => Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))

          //case _ => Effect.unhandled
      case _: Composing =>
        cmd match
          case c: Create => Effect.reply(c.replyTo)(Bad(quizAlreadyExists + c.id))
          case c: Update =>
            if c.title.trim.length < config.minTitleLength then
              Effect.reply(c.replyTo)(Bad(tooShortTitle))
            else if c.recommendedLength < config.minTrialLength then
              Effect.reply(c.replyTo)(Bad(tooShortLength))
            else Effect.persist(Updated(c.title.trim, c.intro, c.recommendedLength))
                      .thenReply(c.replyTo)(_ => Resp.OK)

      case _ => Effect.unhandled

  val eventHandler: (Quiz, Event) => Quiz = (state, evt) =>
    state match
      case _: Blank =>
        evt match
          case Created(id, title, intro, curator, authors, inspectors, length) =>
            Composing(id, title, intro, curator, authors, inspectors, length)
      case composing: Composing =>
        evt match
          case Updated(title, intro, length) =>
            composing.copy(title = title, intro = intro, recommendedLength = length)
