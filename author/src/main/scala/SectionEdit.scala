package quizzly.author

import akka.actor.typed.*

case class SectionEdit(owner: Option[Author], section: Section) extends CborSerializable:
  def nextItemSC: SC = (section.items.map(_.sc.toInt).maxOption.getOrElse(0) + 1).toString
object SectionEdit:

  sealed trait Command extends CborSerializable
  sealed trait CommandWithReply[R] extends Command:
    val replyTo: ActorRef[Resp[R]]
  type CommandOK = CommandWithReply[Nothing]

  sealed trait Event extends CborSerializable

  final case class Create(title: String, owner: Author, quizID: QuizID, 
    replyTo: ActorRef[RespOK]) extends CommandOK
  final case class Created(title: String, owner: Author, quizID: QuizID) extends Event

  final case class Own(owner: Author, replyTo: ActorRef[RespOK]) extends CommandOK
  var alreadyOwned = Reason(2014, "already owned")
  final case class Owned(owner: Author) extends Event

  final case class Update(owner: Author, title: String, replyTo: ActorRef[RespOK]) extends Command
  final case class Updated(title: String) extends Event
  val notOwner = Reason(2012, "not an owner")

  final case class SaveItem(
    owner: Author,
      item: Item,
      sc: Option[SC], // empty if the item is new
      replyTo: ActorRef[Resp[SC]]
  ) extends Command
  val itemNotFound = Reason(2011, "item not found")
  final case class ItemSaved(item: Item) extends Event

  final case class MoveItem(owner: Author, sc: SC, 
    up: Boolean, replyTo: ActorRef[Resp[List[SC]]]) extends CommandWithReply[List[SC]]
  final case class ItemMoved(sc: SC, newOrder: List[SC]) extends Event

  final case class RemoveItem(owner: Author, sc: SC, replyTo: ActorRef[RespOK]) extends CommandOK
  final case class ItemRemoved(sc: SC) extends Event

  case object DiscahrgeInternally extends Command
  final case class Discharge(owner: Author, replyTo: ActorRef[RespOK])
      extends CommandOK
  val notOwned = Reason(2016, "section is not owned")
  case object Discharged extends Event

  final case class Ping(owner: Author, replyTo: ActorRef[RespOK]) extends CommandOK
