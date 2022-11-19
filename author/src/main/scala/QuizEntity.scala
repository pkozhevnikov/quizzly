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
            case c: CommandWithReply[_] =>
              Effect.reply(c.replyTo)(Bad(quizNotFound + state.id))
            case _ =>
              Effect.unhandled

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
            case c: Resolve =>
              if !composing.inspectors(c.inspector) then
                Effect.reply(c.replyTo)(Bad(notInspector))
              else
                Effect.reply(c.replyTo)(Bad(isComposing))

        case review: Review =>
          cmd match
            case c: Create =>
              Effect.reply(c.replyTo)(Bad(quizAlreadyExists + c.id))
            case AddAuthor(author, replyTo) =>
              Effect.persist(AuthorAdded(author)).thenReply(replyTo)(_ => Resp.OK)
            case RemoveAuthor(author, replyTo) =>
              Effect.persist(AuthorRemoved(author)).thenReply(replyTo)(_ => Resp.OK)
            case c: SetReadySign =>
              Effect.reply(c.replyTo)(Bad(onReview))
            case c: Resolve =>
              if !review.composing.inspectors(c.inspector) then
                Effect.reply(c.replyTo)(Bad(notInspector))
              else
                Effect.persist(Resolved(c.inspector, c.approval)).thenReply(c.replyTo)(_ => Resp.OK)
        case released: Released =>
          cmd match
            case c: Create =>
              Effect.reply(c.replyTo)(Bad(quizAlreadyExists + c.id))
            case c: SetObsolete =>
              if released.obsolete then
                Effect.reply(c.replyTo)(Bad(alreadyObsolete))
              else
                Effect.persist(GotObsolete).thenReply(c.replyTo)(_ => Resp.OK)
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
            case AuthorAdded(author) =>
              val comp = review.composing
              review.copy(composing = comp.copy(authors = comp.authors + author))
            case AuthorRemoved(author) =>
              review.copy(composing =
                review.composing.copy(authors = review.composing.authors - author)
              )
            case Resolved(inspector, approval) =>
              var rev = review.copy(
                approvals = review.approvals - inspector,
                disapprovals = review.disapprovals - inspector
              )
              if approval then
                rev = rev.copy(approvals = rev.approvals + inspector)
              else
                rev = rev.copy(disapprovals = rev.disapprovals + inspector)
              if rev.approvals == rev.composing.inspectors then
                Released(
                  rev.composing.id,
                  rev.composing.title,
                  rev.composing.intro,
                  rev.composing.curator,
                  rev.composing.authors,
                  rev.composing.inspectors,
                  rev.composing.recommendedLength,
                  rev.composing.sections,
                  false
                )
              else if rev.disapprovals == rev.composing.inspectors then
                rev.composing.copy(readinessSigns = Set.empty)
              else
                rev

        case released: Released =>
          evt match
            case GotObsolete =>
              released.copy(obsolete = true)

