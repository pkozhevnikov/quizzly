package quizzly.author

import akka.projection.*
import eventsourced.EventEnvelope
import jdbc.JdbcSession

import scala.concurrent.{Future, ExecutionContext}

trait LocalProjectionHandler extends JdbcHandler[EventEnvelope[Quiz.Event], ScalikeJdbcSession]:

  import Quiz.*

  override final def process(session: ScalikeJdbcSession, envelope: EventEnvelope[Quiz.Event]) =
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

  protected def register(event: Created, status: State): Unit
  protected def update(id: QuizID, event: Updated): Unit
  protected def addAuthor(id: QuizID, author: Author): Unit
  protected def addInspector(id: QuizID, inspector: Inspector): Unit
  protected def removeAuthor(id: QuizID, author: Author): Unit
  protected def removeInspector(id: QuizID, inspector: Inspector): Unit
  protected def changeStatus(id: QuizID, status: State): Unit
  protected def setObsolete(id: QuizID): Unit
