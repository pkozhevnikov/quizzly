package quizzly.author

import scalikejdbc.*

class ScalikeLocalProjectionHanlder extends LocalProjectionHandler:

  import Quiz.*

  protected def register(event: Created, status: State) = Future(Done)
  protected def update(id: QuizID, event: Updated) = Future(Done)
  protected def addAuthor(id: QuizID, author: Author) = Future(Done)
  protected def addInspector(id: QuizID, inspector: Inspector) = Future(Done)
  protected def removeAuthor(id: QuizID, author: Author) = Future(Done)
  protected def removeInspector(id: QuizID, inspector: Inspector) = Future(Done)
  protected def changeStatus(id: QuizID, status: State) = Future(Done)
  protected def setObsolete(id: QuizID) = Future(Done)
