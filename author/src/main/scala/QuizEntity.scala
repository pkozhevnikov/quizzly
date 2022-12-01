package quizzly.author

import akka.actor.typed.{Behavior, ActorRef}
import akka.actor.typed.scaladsl.{Behaviors, ActorContext}
import akka.cluster.sharding.typed.scaladsl.{EntityRef, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.*

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.util.{Success, Failure}

import org.slf4j.*

import java.util.concurrent.TimeoutException

object QuizEntity:

  val log = LoggerFactory.getLogger("QuizEntity")

  import Quiz.*

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey("Quiz")

  def apply(id: QuizID, sections: String => EntityRef[SectionEdit.Command], config: QuizConfig)(
      using ExecutionContext
  ): Behavior[Command] = Behaviors.setup { ctx =>
    EventSourcedBehavior[Command, Event, Quiz](
      PersistenceId.ofUniqueId(id),
      Blank(id),
      commandHandler(ctx, sections, config),
      eventHandler
    )
  }

  import Resp.*

  def commandHandler(
      ctx: ActorContext[Command],
      sections: String => EntityRef[SectionEdit.Command],
      config: QuizConfig
  )(using ExecutionContext): (Quiz, Command) => Effect[Event, Quiz] =
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
                  Some(tooShortLength.error())
                else if titl.length < config.minTitleLength then
                  Some(tooShortTitle.error())
                else if insps.size < config.minInspectors then
                  Some(notEnoughInspectors.error())
                else if auths.size < config.minAuthors then
                  Some(notEnoughAuthors.error())
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
              Effect
                .none
                // .thenStop()
                .thenReply(c.replyTo)(_ => Bad(quizNotFound.error() + state.id))

        case composing: Composing =>
          cmd match
            case c: Create =>
              Effect.reply(c.replyTo)(Bad(quizAlreadyExists.error() + c.id))
            case c: Update =>
              if c.title.trim.length < config.minTitleLength then
                Effect.reply(c.replyTo)(Bad(tooShortTitle.error()))
              else if c.recommendedLength < config.minTrialLength then
                Effect.reply(c.replyTo)(Bad(tooShortLength.error()))
              else
                Effect
                  .persist(Updated(c.title.trim, c.intro, c.recommendedLength))
                  .thenReply(c.replyTo)(_ => Resp.OK)
            case c: AddAuthor =>
              if composing.curator == c.author || composing.authors(c.author) ||
                composing.inspectors(c.author)
              then
                Effect.reply(c.replyTo)(Bad(alreadyOnList.error()))
              else
                Effect.persist(AuthorAdded(c.author)).thenReply(c.replyTo)(_ => Resp.OK)
            case c: RemoveAuthor =>
              if !composing.authors(c.author) then
                Effect.reply(c.replyTo)(Bad(notOnList.error()))
              else if composing.authors.size == config.minAuthors then
                Effect.reply(c.replyTo)(Bad(notEnoughAuthors.error()))
              else
                Effect.persist(AuthorRemoved(c.author)).thenReply(c.replyTo)(_ => Resp.OK)
            case c: AddInspector =>
              if composing.curator == c.inspector || composing.authors(c.inspector) ||
                composing.inspectors(c.inspector)
              then
                Effect.reply(c.replyTo)(Bad(alreadyOnList.error()))
              else
                Effect.persist(InspectorAdded(c.inspector)).thenReply(c.replyTo)(_ => Resp.OK)
            case c: RemoveInspector =>
              if !composing.inspectors(c.inspector) then
                Effect.reply(c.replyTo)(Bad(notOnList.error()))
              else if composing.inspectors.size == config.minInspectors then
                Effect.reply(c.replyTo)(Bad(notEnoughInspectors.error()))
              else
                Effect.persist(InspectorRemoved(c.inspector)).thenReply(c.replyTo)(_ => Resp.OK)
            case c: SetReadySign =>
              if composing.readinessSigns(c.author) then
                Effect.reply(c.replyTo)(Bad(alreadySigned.error()))
              else if !composing.authors(c.author) then
                Effect.reply(c.replyTo)(Bad(notAuthor.error()))
              else
                var events = Seq[Event](ReadySignSet(c.author))
                if composing.readinessSigns.size + 1 == composing.authors.size then
                  events :+= GoneForReview
                Effect.persist(events).thenReply(c.replyTo)(_ => Resp.OK)
            case c: Resolve =>
              if !composing.inspectors(c.inspector) then
                Effect.reply(c.replyTo)(Bad(notInspector.error()))
              else
                Effect.reply(c.replyTo)(Bad(isComposing.error()))
            case c: AddSection =>
              if !composing.authors(c.owner) then
                Effect.reply(c.replyTo)(Bad(notAuthor.error()))
              else
                val sc = composing.id + "-" + composing.scCounter
                sections(sc)
                  .ask(SectionEdit.Create(c.title, c.owner, composing.id, _))(2.seconds)
                  .onComplete {
                    case Success(r) =>
                      r match
                        case Resp.OK =>
                          c.replyTo ! Good(sc)
                        case Bad(e) =>
                          c.replyTo ! Bad(e)
                    case Failure(ex) =>
                      ex match
                        case to: TimeoutException =>
                          c.replyTo ! Bad(timedOut.error())
                        case e: Throwable =>
                          c.replyTo ! Bad(unprocessed(ex.getMessage).error())
                  }
                Effect.persist(SCIncrement)
            case c: SaveSection =>
              Effect.persist(SectionSaved(c.section)).thenReply(c.replyTo)(_ => Resp.OK)
            case MoveSection(sc, up, author, replyTo) =>
              if !composing.authors(author) then
                Effect.reply(replyTo)(Bad(notAuthor.error()))
              else
                val idx = composing.sections.indexWhere(_.sc == sc)
                if idx == -1 then
                  Effect.reply(replyTo)(Bad(sectionNotFound.error() + sc))
                else if (idx == 0 && up) || (idx == composing.sections.size - 1 && !up) then
                  Effect.reply(replyTo)(Bad(cannotMove.error()))
                else
                  Effect.persist(SectionMoved(sc, up)).thenReply(replyTo) {
                    case ns: Composing => Good(ns.sections.map(_.sc))
                  }
            case RemoveSection(sc, author, replyTo) =>
              if !composing.authors(author) then
                Effect.reply(replyTo)(Bad(notAuthor.error()))
              else if !composing.sections.exists(_.sc == sc) then
                Effect.reply(replyTo)(Bad(sectionNotFound.error() + sc))
              else
                sections(sc).ask(SectionEdit.GetOwner(_))(2.seconds).onComplete {
                  case Success(r) =>
                    r match
                      case None =>
                        ctx.self ! InternalRemoveSection(sc, replyTo)
                      case Some(owner: Author) =>
                        replyTo ! Bad(SectionEdit.alreadyOwned.error() + owner.name)
                  case Failure(ex) =>
                    replyTo ! Bad(unprocessed(ex.getMessage).error())
                }
                Effect.none
            case InternalRemoveSection(sc, replyTo) =>
              Effect.persist(SectionRemoved(sc)).thenReply(replyTo)(_ => Resp.OK)

            case c: CommandWithReply[_] =>
              Effect.reply(c.replyTo)(Bad(isComposing.error()))

        case review: Review =>
          cmd match
            case c: Create =>
              Effect.reply(c.replyTo)(Bad(quizAlreadyExists.error() + c.id))
            case AddAuthor(author, replyTo) =>
              if review.composing.curator == author || review.composing.authors(author) ||
                review.composing.inspectors(author)
              then
                Effect.reply(replyTo)(Bad(alreadyOnList.error()))
              else
                Effect.persist(AuthorAdded(author)).thenReply(replyTo)(_ => Resp.OK)
            case RemoveAuthor(author, replyTo) =>
              if review.composing.authors.size == config.minAuthors then
                Effect.reply(replyTo)(Bad(notEnoughAuthors.error()))
              else
                Effect.persist(AuthorRemoved(author)).thenReply(replyTo)(_ => Resp.OK)
            case AddInspector(inspector, replyTo) =>
              if review.composing.curator == inspector || review.composing.authors(inspector) ||
                review.composing.inspectors(inspector)
              then
                Effect.reply(replyTo)(Bad(alreadyOnList.error()))
              else
                Effect.persist(InspectorAdded(inspector)).thenReply(replyTo)(_ => Resp.OK)
            case RemoveInspector(inspector, replyTo) =>
              if review.composing.inspectors.size == config.minInspectors then
                Effect.reply(replyTo)(Bad(notEnoughInspectors.error()))
              else
                Effect.persist(InspectorRemoved(inspector)).thenReply(replyTo)(_ => Resp.OK)
            case c: SetReadySign =>
              Effect.reply(c.replyTo)(Bad(onReview.error()))
            case c: Resolve =>
              if !review.composing.inspectors(c.inspector) then
                Effect.reply(c.replyTo)(Bad(notInspector.error()))
              else
                val resolved = review.resolve(c.inspector, c.approval)
                var events = Seq[Event](Resolved(c.inspector, c.approval))
                if resolved.approvals.size == resolved.composing.inspectors.size then
                  events :+= GoneReleased
                else if resolved.disapprovals.size == resolved.composing.inspectors.size then
                  events :+= GoneComposing
                Effect.persist(events).thenReply(c.replyTo)(_ => Resp.OK)
            case c: CommandWithReply[_] =>
              Effect.reply(c.replyTo)(Bad(onReview.error()))

        case released: Released =>
          cmd match
            case c: Create =>
              Effect.reply(c.replyTo)(Bad(quizAlreadyExists.error() + c.id))
            case c: SetObsolete =>
              if released.obsolete then
                Effect.reply(c.replyTo)(Bad(alreadyObsolete.error()))
              else
                Effect.persist(GotObsolete).thenReply(c.replyTo)(_ => Resp.OK)
            case c: CommandWithReply[_] =>
              Effect.reply(c.replyTo)(Bad(quizReleased.error()))

  val eventHandler: (Quiz, Event) => Quiz =
    (state, evt) =>
      state match

        case _: Blank =>
          evt match
            case Created(id, title, intro, curator, authors, inspectors, length) =>
              Composing(id, title, intro, curator, authors, inspectors, length)
            case _ =>
              state

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
              composing.copy(readinessSigns = composing.readinessSigns + author)
            case GoneForReview =>
              Review(composing, Set.empty, Set.empty)
            case SCIncrement =>
              composing.copy(scCounter = composing.scCounter + 1)
            case SectionSaved(section) =>
              if composing.sections.exists(_.sc == section.sc) then
                composing.copy(sections =
                  composing
                    .sections
                    .map { e =>
                      if e.sc == section.sc then
                        section
                      else
                        e
                    }
                )
              else
                composing.copy(sections = composing.sections :+ section)
            case SectionMoved(sc, up) =>
              def move(list: List[Section]): List[Section] = list match
                case Nil => list
                case _ +: Nil => list
                case h +: t => 
                  if (if up then t.head else h).sc == sc then
                    t.head +: (h +: t.tail)
                  else
                    h +: move(t)
              composing.copy(sections = move(composing.sections))
            case _ =>
              state
            case SectionRemoved(sc) =>
              composing.copy(sections = composing.sections.filter(_.sc != sc))

        case review: Review =>
          evt match
            case AuthorAdded(author) =>
              val comp = review.composing
              review.copy(composing = comp.copy(authors = comp.authors + author))
            case AuthorRemoved(author) =>
              review.copy(composing =
                review.composing.copy(authors = review.composing.authors - author)
              )
            case InspectorAdded(inspector) =>
              val comp = review.composing
              review.copy(composing = comp.copy(inspectors = comp.inspectors + inspector))
            case InspectorRemoved(inspector) =>
              review.copy(
                composing = review
                  .composing
                  .copy(inspectors = review.composing.inspectors - inspector),
                approvals = review.approvals - inspector,
                disapprovals = review.disapprovals - inspector
              )
            case Resolved(inspector, approval) =>
              review.resolve(inspector, approval)
            case GoneComposing =>
              review.composing.copy(readinessSigns = Set.empty)
            case GoneReleased =>
              Released(
                review.composing.id,
                review.composing.title,
                review.composing.intro,
                review.composing.curator,
                review.composing.authors,
                review.composing.inspectors,
                review.composing.recommendedLength,
                review.composing.sections,
                false
              )

            case _ =>
              state

        case released: Released =>
          evt match
            case GotObsolete =>
              released.copy(obsolete = true)
            case _ =>
              state
