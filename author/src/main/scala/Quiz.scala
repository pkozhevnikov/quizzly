package quizzly.author

import akka.actor.typed.*

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

  def quizAlreadyExists = Reason(2001, "quiz already exists")
  def tooShortTitle = Reason(2002, "too short title")
  def notEnoughAuthors = Reason(2003, "not enough authors")
  def notEnoughInspectors = Reason(2007, "not enough inspectors")
  def quizNotFound = Reason(2010, "quiz not found")
  def tooShortLength = Reason(2011, "too short recommended trial length")

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
      replyTo: ActorRef[Resp[Nothing]]
  ) extends CommandOK
  final case class Updated(title: String, intro: String, recommendedLength: Int) extends Event

  final case class AddInspector(inspector: Inspector, replyTo: ActorRef[Resp[Nothing]])
      extends CommandOK
  def alreadyOnList = Reason(2015, "already on list")
  final case class InspectorAdded(inspector: Inspector) extends Event
  final case class AddAuthor(author: Author, replyTo: ActorRef[Resp[Nothing]]) extends CommandOK
  final case class AuthorAdded(author: Author) extends Event

  final case class RemoveInspector(inspector: Inspector, replyTo: ActorRef[Resp[Nothing]])
      extends CommandOK
  final case class InspectorRemoved(inspector: Inspector) extends Event
  def notOnList = Reason(2017, "not on list")
  final case class RemoveAuthor(author: Author, replyTo: ActorRef[Resp[Nothing]]) extends CommandOK
  final case class AuthorRemoved(author: Author) extends Event

  final case class AddSection(
      title: String,
      owner: Author,
      replyTo: ActorRef[Resp[SC]] // responds with identifier of new section
  ) extends CommandOK
  final case class SectionAdded(title: String, sc: SC, owner: Author) extends Event

  final case class GrabSection(sc: SC, owner: Author, replyTo: ActorRef[Resp[Nothing]])
      extends CommandOK
  def alreadyGrabbed = Reason(2006, "section already grabbed")
  def sectionNotFound = Reason(2007, "section not found")
  final case class SectionGrabbed(sectionSC: SC, owner: Author, replyTo: ActorRef[Resp[Nothing]])
      extends Event

  final case class DischargeSection(sc: SC, owner: Author, replyTo: ActorRef[Resp[Nothing]])
      extends CommandOK
  def notAnOwner = Reason(2008, "not an owner of section")
  def isNotGrabbed = Reason(2009, "section is not grabbed")
  final case class SectionDischarged(sc: SC) extends Event

  final case class RemoveSection(sc: SC, replyTo: ActorRef[Resp[Nothing]]) extends CommandOK
  final case class SectionRemoved(sc: SC) extends Event

  final case class MoveSection(sc: SC, up: Boolean, replyTo: ActorRef[Resp[List[SC]]])
      extends CommandWithReply[List[SC]]
  final case class SectionMoved(sc: SC, newOrder: List[SC]) extends Event

  final case class SetReadySign(author: Author, replyTo: ActorRef[Resp[Nothing]]) extends CommandOK
  def notAuthor = Reason(2004, "not an author")
  def alreadySigned = Reason(2017, "already signed")
  def onReview = Reason(2020, "quiz on review")
  final case class ReadySignSet(author: Author) extends Event

  final case class UnsetReadySign(author: Author, replyTo: ActorRef[Resp[Nothing]])
      extends CommandOK
  def notSigned = Reason(2019, "not signed")
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
      replyTo: ActorRef[Resp[Nothing]]
  ) extends CommandOK
  def notInspector = Reason(2005, "not an inspector")
  def isComposing = Reason(2018, "quiz is composing")
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

  final case class SetObsolete(replyTo: ActorRef[Resp[Nothing]]) extends CommandOK
  def alreadyObsolete = Reason(2021, "quiz already obsolete")
  def quizReleased = Reason(2022, "quiz released")
  case object GotObsolete extends Event
