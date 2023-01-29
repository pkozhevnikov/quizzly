package quizzly.trial

import java.time.Instant

import com.fasterxml.jackson.core.*
import com.fasterxml.jackson.databind.*

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

type PersonID = String
type ExamID = String
type QuizID = String
type TrialID = String
type ItemIdx = Int

case class Person(id: PersonID, name: String)

type SC = String
type HintIdx = Int
final case class Statement(text: String, image: Option[String]) extends CborSerializable
type Hint = List[Statement]
final case class Item(
    sc: SC,
    intro: String,
    definition: Statement,
    hints: List[Hint],
    hintsVisible: Boolean,
    solutions: List[HintIdx]
) extends CborSerializable:
  def view = ItemView(
    sc,
    intro,
    definition,
    if hintsVisible then
      hints.map(_(0))
    else
      List.empty
    ,
    solutions.size > 1
  )

final case class Section(sc: SC, title: String, intro: String, items: List[Item])
    extends CborSerializable:
  def view = SectionView(title, intro, items.map(_.view))

final case class Quiz(id: QuizID, title: String, intro: String, sections: List[Section])
    extends CborSerializable

final case class ItemView(
    sc: SC,
    intro: String,
    definition: Statement,
    hints: List[Statement],
    multiChoice: Boolean
) extends CborSerializable

final case class SectionView(title: String, intro: String, items: List[ItemView])
    extends CborSerializable

case class ExamPeriod(start: Instant, end: Instant) extends CborSerializable

class PlainPersonSerializer extends JsonSerializer[Person]:
  override def serialize(person: Person, gen: JsonGenerator, serializers: SerializerProvider) = gen
    .writeFieldName(s"${person.id}|${person.name}")

class PlainPersonKeyDeserializer extends KeyDeserializer:
  override def deserializeKey(plain: String, ctx: DeserializationContext): Person =
    val parts = plain.split("|")
    Person(parts(0), parts(1))

class StringPairSerializer extends JsonSerializer[(String, String)]:
  override def serialize(
      pair: (String, String),
      gen: JsonGenerator,
      serializers: SerializerProvider
  ) = gen.writeFieldName(s"${pair(0)}|${pair(1)}")
class StringPairKeyDeserializer extends KeyDeserializer:
  override def deserializeKey(plain: String, ctx: DeserializationContext): (String, String) =
    val parts = plain.split("|")
    (parts(0), parts(1))
