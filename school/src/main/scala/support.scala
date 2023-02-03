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
type SC = String
type TrialID = String

final case class ExamConfig(
    preparationPeriodHours: Int,
    trialLengthMinutesRange: (Int, Int),
    trackerCheckRateMinutes: Int = 5,
    awakeExamBeforeProceedMinutes: Int = 3,
    passingGrade: (Int, Int) = (50, 65)
    )
object ExamConfig:
  def fromConfig(econf: com.typesafe.config.Config) = ExamConfig(
    econf.getInt("preparationPeriodHours"),
    (econf.getInt("trialLengthMinutes.min"), econf.getInt("trialLengthMinutes.max")),
    econf.getInt("trackerCheckRateMinutes"),
    econf.getInt("awakeExamBeforeProceedMinutes"),
    (econf.getInt("passingGrade.min"), econf.getInt("passingGrade.default"))
  )

final case class Quiz(id: QuizID, title: String)

final case class PersonRef(id: PersonID, name: String) extends CborSerializable

given Conversion[grpc.Statement, Statement] = g => Statement(g.text, g.image)
given Conversion[grpc.Item, Item] =
  g =>
    Item(
      g.sc,
      g.intro,
      g.definition,
      g.hints.map(_.alts.map(gs => gs: Statement).toList).toList,
      g.hintsVisible,
      g.solutions.toList
    )
given Conversion[grpc.Section, Section] =
  g => Section(g.sc, g.title, g.intro, g.items.map(gi => gi: Item).toList)
given Conversion[grpc.Person, PersonRef] = g => PersonRef(g.id, g.name)
given Conversion[grpc.RegisterQuizRequest, FullQuiz] = 
  g => FullQuiz(
    g.id,
    g.title,
    g.intro,
    g.recommendedTrialLength,
    g.curator,
    g.authors.map(a => a: PersonRef).toSet,
    g.inspectors.map(i => i: PersonRef).toSet,
    g.sections.map(s => s: Section).toList
  )

final case class FullQuiz(
    id: QuizID,
    title: String,
    intro: String,
    recommendedTrialLength: Int,
    curator: PersonRef,
    authors: Set[PersonRef],
    inspectors: Set[PersonRef],
    sections: List[Section]
) extends CborSerializable
final case class Section(sc: SC, title: String, intro: String, items: List[Item])
    extends CborSerializable
final case class Item(
    sc: SC,
    intro: String,
    definition: Statement,
    hints: List[List[Statement]],
    hintsVisible: Boolean,
    solutions: List[Int]
) extends CborSerializable
final case class Statement(text: String, image: Option[String]) extends CborSerializable

def genGrpcQuiz(n: Int) = grpc.RegisterQuizRequest(
  s"Q-$n",
  s"Q-$n title",
  s"Q-$n intro",
  45,
  grpc.Person("pers1", "pers1 name"),
  for (p <- 2 to 4)
    yield grpc.Person(s"pers$p", s"pers$p name"),
  for (p <- 5 to 6)
    yield grpc.Person(s"pers$p", s"pers$p name"),
  Seq(
    grpc.Section(
      s"Q-$n-1",
      "section title",
      "section intro",
      Seq(
        grpc.Item(
          "1",
          "item intro",
          grpc.Statement("def text", Some("def-image")),
          Seq(
            grpc
              .Hint(Seq(grpc.Statement("hint1 alt 1", None), grpc.Statement("hint1 alt 2", None))),
            grpc.Hint(Seq(grpc.Statement("hint2 alt 1", None)))
          )
        )
      )
    )
  )
)
