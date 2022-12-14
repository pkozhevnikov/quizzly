package quizzly.author

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class QuizListed(
    id: String,
    title: String,
    obsolete: Boolean,
    curator: Person,
    authors: Set[Person],
    inspectors: Set[Person],
    state: Quiz.State
)

trait Read:

  def getList()(using ExecutionContext): Future[List[QuizListed]]
