package quizzly.author

import akka.projection.*
import eventsourced.EventEnvelope
import scaladsl.Handler

import scala.concurrent.{Future, ExecutionContext}

import akka.Done

trait LocalProjectionHandler(using ExecutionContext) extends Handler[EventEnvelope[Quiz.Event]]:

  import Quiz.*

  override final def process(envelope: EventEnvelope[Quiz.Event]) =
    val id = envelope.persistenceId
    envelope.event match
      case e: Created =>
        register(e, State.COMPOSING)
      case e: Updated =>
        update(id, e)
      case AuthorAdded(author) =>
        addAuthor(id, author)
      case InspectorAdded(inspector) =>
        addInspector(id, inspector)
      case AuthorRemoved(author) =>
        removeAuthor(id, author)
      case InspectorRemoved(inspector) =>
        removeInspector(id, inspector)
      case GoneForReview =>
        changeStatus(id, State.REVIEW)
      case GoneComposing =>
        changeStatus(id, State.COMPOSING)
      case GoneReleased =>
        changeStatus(id, State.RELEASED)
      case GotObsolete =>
        setObsolete(id)

      case _ =>
        Future(Done)

  protected def register(event: Created, status: State): Future[Done]
  protected def update(id: QuizID, event: Updated): Future[Done]
  protected def addAuthor(id: QuizID, author: Author): Future[Done]
  protected def addInspector(id: QuizID, inspector: Inspector): Future[Done]
  protected def removeAuthor(id: QuizID, author: Author): Future[Done]
  protected def removeInspector(id: QuizID, inspector: Inspector): Future[Done]
  protected def changeStatus(id: QuizID, status: State): Future[Done]
  protected def setObsolete(id: QuizID): Future[Done]
