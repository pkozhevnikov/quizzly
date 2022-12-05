package quizzly.author

import scala.concurrent.Future

case class QuizListed(
    id: String,
    title: String,
    obsolete: Boolean,
    curator: Person,
    authors: Set[Person],
    inspectors: Set[Person]
)

trait Read:

  def getList(): Future[List[QuizListed]]
