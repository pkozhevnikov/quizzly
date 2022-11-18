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

case class Error(code: Int, reason: String, clues: Seq[String] = Seq.empty)
    extends CborSerializable:
  def +(clue: String): Error = Error(code, reason, clues :+ clue)
  def ++(clues: Seq[String]): Error = Error(code, reason, this.clues ++ clues)

//enum Resp[+R] extends CborSerializable:
//  case Good(r: R)
//  case Bad(e: Error)
//  case OK

sealed trait Resp[+V] extends CborSerializable
type RespOK = Resp[Nothing]
object Resp:
  case object OK extends RespOK
final case class Good[+V](v: V) extends Resp[V]
final case class Bad[+V](e: Error) extends Resp[V]

case class QuizConfig(minAuthors: Int, minInspectors: Int, minTrialLength: Int, minTitleLength: Int)
