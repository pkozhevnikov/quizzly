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

  def commandHandler(config: QuizConfig): (Quiz, Command) => Effect[Event, Quiz] =
    (state, cmd) =>
      state match
        case _: Blank =>
          cmd match
            case Create(id, title, intro, curator, authors, inspectors, length, replyTo) =>
              val insps = inspectors - curator -- authors
              val auths = authors - curator
              val titl = title.trim
              (
                if length < config.minTrialLength then
                  Some(tooShortLength)
                else if titl.length < config.minTitleLength then
                  Some(tooShortTitle)
                else if insps.size < config.minInspectors then
                  Some(notEnoughInspectors)
                else if auths.size < config.minAuthors then
                  Some(notEnoughAuthors)
                else
                  None
              )
              match
                case Some(error) =>
                  Effect.reply(replyTo)(Bad(error))
                case _ =>
                  Effect
                    .persist(Created(id, titl, intro, curator, auths, insps, length))
                    .thenReply(replyTo) { case s: Composing =>
                      Good(CreateDetails(s.authors, s.inspectors))
                    }
            case c: Update =>
              Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
            case c: AddInspector =>
              Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
            case c: AddAuthor =>
              Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
            case c: RemoveInspector =>
              Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
            case c: RemoveAuthor =>
              Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
            case c: AddSection =>
              Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
            case c: SetObsolete =>
              Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
            case c: GrabSection =>
              Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
            case c: DischargeSection =>
              Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
            case c: MoveSection =>
              Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
            case c: RemoveSection =>
              Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
            case c: SetReadySign =>
              Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
            case c: Resolve =>
              Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))

        // case _ => Effect.unhandled
        case composing: Composing =>
          cmd match
            case c: Create =>
              Effect.reply(c.replyTo)(Bad(quizAlreadyExists + c.id))
            case c: Update =>
              if c.title.trim.length < config.minTitleLength then
                Effect.reply(c.replyTo)(Bad(tooShortTitle))
              else if c.recommendedLength < config.minTrialLength then
                Effect.reply(c.replyTo)(Bad(tooShortLength))
              else
                Effect
                  .persist(Updated(c.title.trim, c.intro, c.recommendedLength))
                  .thenReply(c.replyTo)(_ => Resp.OK)
            case c: AddAuthor =>
              if composing.curator == c.author || composing.authors(c.author) ||
                composing.inspectors(c.author)
              then
                Effect.reply(c.replyTo)(Bad(alreadyOnList))
              else
                Effect.persist(AuthorAdded(c.author)).thenReply(c.replyTo)(_ => Resp.OK)
            case c: RemoveAuthor =>
              if !composing.authors(c.author) then
                Effect.reply(c.replyTo)(Bad(notOnList))
              else if composing.authors.size == config.minAuthors then
                Effect.reply(c.replyTo)(Bad(notEnoughAuthors))
              else
                Effect.persist(AuthorRemoved(c.author)).thenReply(c.replyTo)(_ => Resp.OK)
            case c: AddInspector =>
              if composing.curator == c.inspector || composing.authors(c.inspector) ||
                composing.inspectors(c.inspector)
              then
                Effect.reply(c.replyTo)(Bad(alreadyOnList))
              else
                Effect.persist(InspectorAdded(c.inspector)).thenReply(c.replyTo)(_ => Resp.OK)
            case c: RemoveInspector =>
              if !composing.inspectors(c.inspector) then
                Effect.reply(c.replyTo)(Bad(notOnList))
              else
                Effect.persist(InspectorRemoved(c.inspector)).thenReply(c.replyTo)(_ => Resp.OK)
            case c: SetReadySign =>
              if composing.readinessSigns(c.author) then
                Effect.reply(c.replyTo)(Bad(alreadySigned))
              else if !composing.authors(c.author) then
                Effect.reply(c.replyTo)(Bad(notAuthor))
              else
                Effect.persist(ReadySignSet(c.author)).thenReply(c.replyTo)(_ => Resp.OK)

        case review: Review =>
          cmd match
            case c: Create =>
              Effect.reply(c.replyTo)(Bad(quizAlreadyExists + c.id))
            case RemoveAuthor(author, replyTo) =>
              Effect.persist(AuthorRemoved(author)).thenReply(replyTo)(_ => Resp.OK)
        case released: Released =>
          cmd match
            case c: Create =>
              Effect.reply(c.replyTo)(Bad(quizAlreadyExists + c.id))
        case _ =>
          Effect.unhandled

  val eventHandler: (Quiz, Event) => Quiz =
    (state, evt) =>
      state match
        case _: Blank =>
          evt match
            case Created(id, title, intro, curator, authors, inspectors, length) =>
              Composing(id, title, intro, curator, authors, inspectors, length)
        case composing: Composing =>
          evt match
            case Updated(title, intro, length) =>
              composing.copy(title = title, intro = intro, recommendedLength = length)
            case AuthorAdded(author) =>
              composing.copy(authors = composing.authors + author)
            case AuthorRemoved(author) =>
              composing.copy(
                authors = composing.authors - author,
                readinessSigns = composing.readinessSigns - author
              )
            case InspectorAdded(inspector) =>
              composing.copy(inspectors = composing.inspectors + inspector)
            case InspectorRemoved(inspector) =>
              composing.copy(inspectors = composing.inspectors - inspector)
            case ReadySignSet(author) =>
              val comp = composing.copy(readinessSigns = composing.readinessSigns + author)
              if comp.readinessSigns == comp.authors then
                Review(comp, Set.empty, Set.empty)
              else
                comp
        case review: Review =>
          evt match
            case AuthorRemoved(author) =>
              review.copy(composing =
                review.composing.copy(authors = review.composing.authors - author)
              )
