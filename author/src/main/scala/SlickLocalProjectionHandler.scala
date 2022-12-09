package quizzly.author

import slick.basic.DatabaseConfig

import scala.concurrent.{ExecutionContext, Future}

import akka.Done

object SlickLocalProjectionHandler:

  def apply(dbConfig: DatabaseConfig[?])(using ExecutionContext) =
    new SlickLocalProjectionHandler(dbConfig)

class SlickLocalProjectionHandler(dbConfig: DatabaseConfig[?])(using ExecutionContext)
    extends LocalProjectionHandler:

  import Quiz.*

  private val db = dbConfig.db

  override protected def register(event: Created, status: State) = Future(Done)
  override protected def update(id: QuizID, event: Updated) = Future(Done)
  override protected def addAuthor(id: QuizID, author: Author) = Future(Done)
  override protected def addInspector(id: QuizID, inspector: Inspector) = Future(Done)
  override protected def removeAuthor(id: QuizID, author: Author) = Future(Done)
  override protected def removeInspector(id: QuizID, inspector: Inspector) = Future(Done)
  override protected def changeStatus(id: QuizID, status: State) = Future(Done)
  override protected def setObsolete(id: QuizID) = Future(Done)
