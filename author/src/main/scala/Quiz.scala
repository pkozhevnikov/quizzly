package quizzly.author

import akka.actor.typed.*

// internal identifier of Sections and Items
// that is generated by their contaiers
// (Quiz and Section respectively)
type SC = String

type HintIdx = Int

final case class Statement(text: String, image: Option[String]) extends CborSerializable

type Hint = Set[Statement]

final case class Item(
    sc: SC,
    intro: String,
    definition: Statement,
    hints: List[Hint],
    hintsVisible: Boolean,
    solutions: List[HintIdx]
) extends CborSerializable

final case class Section(
    sc: SC,
    title: String,
    items: List[Item]
) extends CborSerializable

sealed trait Quiz extends CborSerializable:
  def id: QuizID

object Quiz:

  final case class Blank(id: QuizID) extends Quiz

  sealed trait Command extends CborSerializable
  sealed trait CommandWithReply[R] extends Command:
    val replyTo: ActorRef[Resp[R]]
  type CommandOK = CommandWithReply[Nothing]

  sealed trait Event extends CborSerializable

  final case class Create(
      id: QuizID,
      title: String,
      intro: String,
      curator: Curator,
      authors: Set[Author],
      inspectors: Set[Inspector],
      recommendedLength: Int, // length in minutes
      replyTo: ActorRef[Resp[CreateDetails]]
  ) extends CommandWithReply[CreateDetails]
  final case class CreateDetails(authors: Set[Author], inspectors: Set[Inspector])

  val quizAlreadyExists = Reason(2001, "quiz already exists")
  val tooShortTitle = Reason(2002, "too short title")
  val notEnoughAuthors = Reason(2003, "not enough authors")
  val notEnoughInspectors = Reason(2007, "not enough inspectors")
  val quizNotFound = Reason(2010, "quiz not found")
  val tooShortLength = Reason(2011, "too short recommended trial length")

  final case class Created(
      id: QuizID,
      title: String,
      intro: String,
      curator: Curator,
      authors: Set[Author],
      inspectors: Set[Inspector],
      recommendedLength: Int
  ) extends Event

  final case class Composing(
      id: QuizID,
      title: String,
      intro: String,
      curator: Curator,
      authors: Set[Author],
      inspectors: Set[Inspector],
      recommendedLength: Int,
      readinessSigns: Set[Author] = Set.empty,
      sections: List[Section] = List.empty
  ) extends Quiz:
    def nextSectionSC: SC =
      id + "-" + (sections.map(_.sc.split("\\-").last).map(_.toInt).maxOption.getOrElse(0) + 1)

  final case class Update(
      title: String,
      intro: String,
      recommendedLength: Int,
      replyTo: ActorRef[RespOK]
  ) extends CommandOK
  final case class Updated(title: String, intro: String, recommendedLength: Int) extends Event

  final case class AddInspector(inspector: Inspector, replyTo: ActorRef[RespOK])
      extends CommandOK
  val alreadyOnList = Reason(2015, "already on list")
  final case class InspectorAdded(inspector: Inspector) extends Event
  final case class AddAuthor(author: Author, replyTo: ActorRef[RespOK]) extends CommandOK
  final case class AuthorAdded(author: Author) extends Event

  final case class RemoveInspector(inspector: Inspector, replyTo: ActorRef[RespOK])
      extends CommandOK
  final case class InspectorRemoved(inspector: Inspector) extends Event
  val notOnList = Reason(2017, "not on list")
  final case class RemoveAuthor(author: Author, replyTo: ActorRef[RespOK]) extends CommandOK
  final case class AuthorRemoved(author: Author) extends Event

  final case class AddSection(
      title: String,
      owner: Author,
      replyTo: ActorRef[Resp[SC]] // responds with identifier of new section
  ) extends CommandWithReply[SC]

  final case class SaveSection(section: Section, replyTo: ActorRef[RespOK]) extends CommandOK
  final case class SectionSaved(section: Section) extends Event

  final case class OwnSection(sc: SC, owner: Author, replyTo: ActorRef[RespOK])
      extends CommandOK
  val sectionNotFound = Reason(2007, "section not found")
  final case class SectionOwned(sc: SC, owner: Author, replyTo: ActorRef[RespOK])
      extends Event

  final case class RemoveSection(sc: SC, replyTo: ActorRef[RespOK]) extends CommandOK
  final case class SectionRemoved(sc: SC) extends Event

  final case class MoveSection(sc: SC, up: Boolean, replyTo: ActorRef[Resp[List[SC]]])
      extends CommandWithReply[List[SC]]
  final case class SectionMoved(sc: SC, newOrder: List[SC]) extends Event

  final case class SetReadySign(author: Author, replyTo: ActorRef[RespOK]) extends CommandOK
  val notAuthor = Reason(2004, "not an author")
  val alreadySigned = Reason(2017, "already signed")
  val onReview = Reason(2020, "quiz on review")
  final case class ReadySignSet(author: Author) extends Event

  final case class UnsetReadySign(author: Author, replyTo: ActorRef[RespOK])
      extends CommandOK
  val notSigned = Reason(2019, "not signed")
  final case class ReadySignUnset(author: Author) extends Event

  case object GoneForReview extends Event
  final case class Review(
      composing: Composing,
      approvals: Set[Inspector],
      disapprovals: Set[Inspector]
  ) extends Quiz:
    override def id: QuizID = composing.id
    def resolve(inspector: Inspector, approval: Boolean) =
      val resolutions =
        if approval then
          (approvals + inspector) -> (disapprovals - inspector)
        else
          (approvals - inspector) -> (disapprovals + inspector)
      Review(composing, resolutions(0), resolutions(1))

  final case class Resolve(
      inspector: Inspector,
      approval: Boolean,
      replyTo: ActorRef[RespOK]
  ) extends CommandOK
  val notInspector = Reason(2005, "not an inspector")
  val isComposing = Reason(2018, "quiz is composing")
  final case class Resolved(inspector: Inspector, approval: Boolean) extends Event
  case object GoneReleased extends Event
  case object GoneComposing extends Event
  final case class Released(
      id: QuizID,
      title: String,
      intro: String,
      curator: Curator,
      authors: Set[Author],
      inspectors: Set[Inspector],
      recommendedLength: Int,
      sections: List[Section],
      obsolete: Boolean
  ) extends Quiz

  final case class SetObsolete(replyTo: ActorRef[RespOK]) extends CommandOK
  val alreadyObsolete = Reason(2021, "quiz already obsolete")
  val quizReleased = Reason(2022, "quiz released")
  case object GotObsolete extends Event
