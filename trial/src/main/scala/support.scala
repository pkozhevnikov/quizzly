package quizzly.trial

import java.time.ZonedDateTime

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

case class Quiz(id: QuizID)

case class ExamPeriod(start: ZonedDateTime, end: ZonedDateTime)

class PlainPersonSerializer extends JsonSerializer[Person]:
  override def serialize(person: Person, gen: JsonGenerator, serializers: SerializerProvider) = 
    gen.writeFieldName(s"${person.id}|${person.name}")

class PlainPersonKeyDeserializer extends KeyDeserializer:
  override def deserializeKey(plain: String, ctx: DeserializationContext): Person =
    val parts = plain.split("|")
    Person(parts(0), parts(1))
  
