package quizzly.author

type QuizID = String
type PersonID = String
type AuthorID = String
type InspectorID = String

final case class Person(id: PersonID, name: String)

type Curator = Person
type Author = Person
type Inspector = Person

trait CborSerializable

final case class Reason(code: Int, phrase: String) extends CborSerializable:
  def error(): Error = Error(this, Seq.empty)

final case class Error(reason: Reason, clues: Seq[String]) extends CborSerializable:
  def +(clue: String) = Error(reason, clues :+ clue)
  def ++(clues: Seq[String]) = Error(reason, this.clues :++ clues)

sealed trait Resp[+R] extends CborSerializable
object Resp:
  case class Good[R](value: R) extends Resp[R]
  case class Bad[R](error: Error) extends Resp[R]
  case object OK extends Resp[Nothing]

type RespOK = Resp[Nothing]

case class QuizConfig(minAuthors: Int, minInspectors: Int, minTrialLength: Int, minTitleLength: Int)
