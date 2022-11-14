package quizzly.author

type QuizID = String
type PersonID = String
type AuthorID = String
type InspectorID = String

final case class Person(id: PersonID, name: String)
type Curator = Person
type Author = Person
type Inspector = Person

sealed trait Quiz:
  val id: QuizID

object Quiz:

  final case class Blank(id: QuizID) extends Quiz
  
  type HintIdx = Int

  final case class Statement(text: String, image: Option[String])
  final case class Item(
    intro: String,
    definition: Statement,
    hints: List[List[Statement]],
    hintsVisible: Boolean,
    solutions: List[HintIdx]
  )

    

  final case class Composing(
    id: QuizID,
    title: String,
    intro: String,
    curator: Curator,
    authors: Set[Author],
    inspectors: Set[Inspector],
    recommendedLength: Duration,
    readinessSigns: Set[AuthorID] = Nil,
    sections: List[Section] = Nil
  ) extends Quiz
    
