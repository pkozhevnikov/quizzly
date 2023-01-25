package quizzly.trial

import java.time.Instant
import akka.actor.typed.ActorRef

type SC = String

type HintIdx = Int

final case class Statement(text: String, image: Option[String]) extends CborSerializable

type Hint = List[Statement]

final case class ItemView(sc: SC, intro: String, definition: Statement, hints: List[Hint])
    extends CborSerializable

final case class SectionView(sc: SC, title: String, intro: String, items: List[ItemView])
    extends CborSerializable

case class Trial(
    testee: Person,
    exam: ExamID,
    quiz: Quiz,
    length: Int,
    startedAt: Instant,
    finishedAt: Option[Instant],
    solutions: Map[(SC, SC), List[Int]] // key: (sectionSC, itemSC)
) extends CborSerializable

object Trial:

  sealed trait Command extends CborSerializable
  sealed trait CommandWithReply[R] extends Command:
    def replyTo: ActorRef[Resp[R]]

  sealed trait Event extends CborSerializable

  case class Start(testee: Person, exam: ExamID, replyTo: ActorRef[Resp[Instant]])
      extends CommandWithReply[Instant]
  val trialAlreadyStarted = Reason(3001, "trial already started")
  val trialFinalized = Reason(3002, "trial finalized")
  val notTestee = Reason(3003, "user is not a testee of this exam")
  val examEnded = Reason(3004, "exam ended")
  val examNotFound = Reason(3005, "exam not found")
  case class Started(testee: Person, exam: ExamID, quiz: Quiz, length: Int, at: Instant)
      extends Event

  case class Submit(
      sectionSC: SC,
      itemSC: SC,
      solution: List[Int],
      replyTo: ActorRef[Resp[Option[SectionView]]]
  ) extends CommandWithReply[Option[SectionView]]
  val itemNotFound = Reason(3006, "item not found")
  val trialNotStarted = Reason(3007, "trial not started")
  case class Submitted(sectionSC: SC, itemSC: SC, solution: List[Int]) extends Event

  case class Finalize(replyTo: ActorRef[RespOK]) extends CommandWithReply[Nothing]
  case class Finalized(at: Instant) extends Event
