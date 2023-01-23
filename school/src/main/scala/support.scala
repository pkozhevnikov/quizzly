package quizzly.school

import java.time.*
import com.fasterxml.jackson.annotation.*

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

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(value = classOf[Official], name = "Official"),
    new JsonSubTypes.Type(value = classOf[Student], name = "Student")
  )
)
sealed trait Person extends CborSerializable:
  val place: String
  val id: PersonID
  val name: String
object Person:
  def of(place: String)(id: PersonID, name: String): Person =
    place match
      case "Official" =>
        Official(id, name)
      case "Student" =>
        Student(id, name)
      case x =>
        throw IllegalArgumentException(s"unknown person place $x")
case class Official(id: PersonID, name: String) extends Person:
  val place = "Official"
case class Student(id: PersonID, name: String) extends Person:
  val place = "Student"

type QuizID = String
type ExamID = String

final case class ExamConfig(
    preparationPeriodHours: Int,
    trialLengthMinutesRange: (Int, Int),
    trackerCheckRateMinutes: Int = 5,
    awakeExamBeforeProceedMinutes: Int = 3
)
object ExamConfig:
  def fromConfig(econf: com.typesafe.config.Config) = ExamConfig(
    econf.getInt("preparationPeriodHours"),
    (econf.getInt("trialLengthMinutes.min"), econf.getInt("trialLengthMinutes.max")),
    econf.getInt("trackerCheckRateMinutes"),
    econf.getInt("awakeExamBeforeProceedMinutes")
  )

final case class Quiz(id: QuizID, title: String)
