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

sealed trait Person:
  val place: String
  val id: PersonID
  val name: String
case class Official(id: PersonID, name: String) extends Person:
  val place = "Official"
case class Student(id: PersonID, name: String) extends Person:
  val place = "Student"

type QuizID = String
type ExamID = String

final case class ExamConfig(preparationPeriodHours: Int)

final case class Quiz(id: QuizID, title: String)
