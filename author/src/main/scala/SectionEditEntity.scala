package quizzly.author

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.TimerScheduler
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.*
import org.slf4j.*

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

object SectionEditEntity:

  var log = LoggerFactory.getLogger("SectionEditEntity")

  import SectionEdit.*
  import Resp.*

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey("SectionEdit")

  val inact = "Inactive"

  extension (timer: TimerScheduler[Command])
    def reset(period: Int) =
      timer.cancel(inact)
      timer.startSingleTimer(inact, DischargeInactive, FiniteDuration(period, MINUTES))

  def apply(id: SC, quizzes: String => EntityRef[Quiz.Command], config: QuizConfig)(using
      ExecutionContext
  ): Behavior[Command] = Behaviors.withTimers { timer =>
    Behaviors.setup { ctx =>
      EventSourcedBehavior[Command, Event, Option[SectionEdit]](
        PersistenceId(EntityKey.name, id),
        None,
        (state, cmd) =>
          state match
            case None =>
              cmd match
                case Create(title, owner, quizID, replyTo) =>
                  if title.trim().length() < config.minTitleLength then
                    Effect.reply(replyTo)(Bad(Quiz.tooShortTitle.error()))
                  else
                    Effect
                      .persist(Created(title.trim(), owner, quizID))
                      .thenReply(replyTo) { _ =>
                        timer.reset(config.inactivityMinutes)
                        Resp.OK
                      }
                case c: CommandWithReply[?] =>
                  Effect.reply(c.replyTo)(Bad(Quiz.sectionNotFound.error()))
                case _ =>
                  Effect.unhandled

            case Some(edit) =>
              cmd match
                case c: Create =>
                  Effect.reply(c.replyTo)(Bad(alreadyExists.error()))
                case c: GetOwner =>
                  Effect.reply(c.replyTo)(Good(edit.owner))
                case c: CommandWithOwnerReply[?] =>
                  edit.owner match
                    case Some(person) =>
                      if c.owner != person then
                        Effect.reply(c.replyTo)(Bad(notOwner.error()))
                      else
                        timer.reset(config.inactivityMinutes)
                        takeCommand(edit, cmd, quizzes, ctx, config)
                    case _ =>
                      cmd match
                        case _: Own =>
                          timer.reset(config.inactivityMinutes)
                          Effect.persist(Owned(c.owner)).thenReply(c.replyTo)(_ => Resp.OK)
                        case _ =>
                          Effect.reply(c.replyTo)(Bad(notOwned.error()))
                case _ =>
                  takeCommand(edit, cmd, quizzes, ctx, config)
        ,
        (state, evt) =>
          state match
            case None =>
              evt match
                case Created(title, owner, quizID) =>
                  Some(SectionEdit(Some(owner), Section(id, title, "", List.empty), quizID))
                case _ =>
                  state
            case Some(edit) =>
              Some(takeEvent(edit, evt))
      )
    }
  }

  def takeCommand(
      edit: SectionEdit,
      cmd: Command,
      quizzes: String => EntityRef[Quiz.Command],
      ctx: ActorContext[Command],
      config: QuizConfig
  )(using ExecutionContext): Effect[Event, Option[SectionEdit]] =
    cmd match
      case Ping(_, replyTo) =>
        Effect.reply(replyTo)(Resp.OK)
      case Update(_, title, intro, replyTo) =>
        if title.trim().length < config.minTitleLength then
          Effect.reply(replyTo)(Bad(Quiz.tooShortTitle.error()))
        else
          Effect.persist(Updated(title.trim(), intro)).thenReply(replyTo)(_ => Resp.OK)
      case DischargeInactive =>
        ctx.self !
          Discharge(
            edit.owner.get,
            ctx.spawnAnonymous(
              Behaviors.receiveMessage[RespOK] {
                case Resp.OK | Good(_) =>
                  log.debug("discharged as inactive {} {}", edit.section.sc, edit.owner.get)
                  Behaviors.stopped
                case Bad(e) =>
                  log.error("cannot discharge: {}", e)
                  Behaviors.stopped
              }
            )
          )
        Effect.none
      case c: Own =>
        Effect.reply(c.replyTo)(Bad(alreadyOwned.error()))
      case Discharge(_, replyTo) =>
        quizzes(edit.quizID)
          .ask(Quiz.SaveSection(edit.section, _))(2.seconds)
          .onComplete {
            case Success(r) =>
              r match
                case Good(_) =>
                  ctx.self ! InternalDischarge(replyTo)
                case Bad(e) =>
                  replyTo ! Bad(e)
            case Failure(ex) =>
              log.error("quiz could not save section", ex)
              replyTo ! Bad(Quiz.unprocessed(ex.getMessage).error())
          }
        Effect.none
      case InternalDischarge(replyTo) =>
        Effect.persist(Discharged).thenReply(replyTo)(_ => Resp.OK)
      case AddItem(_, replyTo) =>
        val sc = edit.nextItemSC()
        Effect
          .persist(ItemSaved(Item(sc, "", Statement("", None), List.empty, false, List.empty)))
          .thenReply(replyTo)(_ => Good(sc))
      case SaveItem(_, item, replyTo) =>
        Effect.persist(ItemSaved(item)).thenReply(replyTo)(_ => Resp.OK)
      case RemoveItem(_, sc, replyTo) =>
        if !edit.section.items.exists(_.sc == sc) then
          Effect.reply(replyTo)(Bad(itemNotFound.error() + sc))
        else
          Effect.persist(ItemRemoved(sc)).thenReply(replyTo)(_ => Resp.OK)
      case MoveItem(_, sc, up, replyTo) =>
        if edit.section.items.isEmpty || !edit.section.items.exists(_.sc == sc) then
          Effect.reply(replyTo)(Bad(itemNotFound.error() + sc))
        else if (up && edit.section.items.head.sc == sc) ||
          (!up && edit.section.items.last.sc == sc)
        then
          Effect.reply(replyTo)(Bad(cannotMove.error()))
        else
          Effect
            .persist(ItemMoved(sc, up))
            .thenReply(replyTo)(s => Good(s.get.section.items.map(_.sc)))
      case _ =>
        Effect.unhandled

  def takeEvent(edit: SectionEdit, evt: Event): SectionEdit =
    evt match
      case Updated(title, intro) =>
        edit.copy(section = edit.section.copy(title = title, intro = intro))
      case Discharged =>
        edit.copy(owner = None)
      case ItemSaved(item) =>
        val idx = edit.section.items.indexWhere(_.sc == item.sc)
        if idx == -1 then
          val section = edit.section.copy(items = edit.section.items :+ item)
          edit.copy(section = section)
        else
          edit.copy(section = edit.section.copy(items = edit.section.items.updated(idx, item)))
      case ItemRemoved(sc) =>
        edit.copy(section = edit.section.copy(items = edit.section.items.filterNot(_.sc == sc)))
      case ItemMoved(sc, up) =>
        val l = edit.section.items
        val from = l.indexWhere(_.sc == sc)
        val to =
          if up then
            from - 1
          else
            from + 1
        edit.copy(section =
          edit.section.copy(items = l.patch(from, List(), 1).patch(to, List(l(from)), 0))
        )
      case Owned(owner) =>
        edit.copy(owner = Some(owner))

      case _ =>
        edit
