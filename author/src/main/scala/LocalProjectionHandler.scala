package quizzly.author

import akka.projection.*
import scalikejdbc.*

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import eventsourced.EventEnvelope
import jdbc.scaladsl.JdbcHandler

class LocalProjectionHandler extends JdbcHandler[EventEnvelope[Quiz.Event], ScalikeJdbcSession]:

  import Quiz.*

  val insMember = "insert into member (id,role,person_id,name) values (?,?,?,?)"
  val delMember = "delete from member where id=? and person_id=?"

  override final def process(session: ScalikeJdbcSession, envelope: EventEnvelope[Quiz.Event]) =
    val id = envelope.persistenceId.split("\\|")(1)
    given ScalikeJdbcSession = session
    given QuizID = id
    def update[Result](expr: DBSession => Result) = session.db.withinTx(expr(_))
    envelope.event match
      case e: Created =>
        update { implicit session =>
          sql"delete from quiz where id=?".bind(id).execute.apply()
          sql"insert into quiz (id,title,status,obsolete) values (?,?,?,?)"
            .bind(id, e.title, State.Composing.toString, false)
            .update
            .apply()
          SQL(insMember).bind(id, 1, e.curator.id, e.curator.name).update.apply()
          e.authors
            .foreach { a =>
              SQL(insMember).bind(id, 2, a.id, a.name).update.apply()
            }
          e.inspectors
            .foreach { i =>
              SQL(insMember).bind(id, 3, i.id, i.name).update.apply()
            }
        }
      case e: Updated =>
        update { implicit session =>
          sql"update quiz set title=? where id=?".bind(e.title, id).update.apply()
        }
      case AuthorAdded(author) =>
        update { implicit session =>
          SQL(delMember).bind(id, author.id).execute.apply()
          SQL(insMember).bind(id, 2, author.id, author.name).update.apply()
        }
      case InspectorAdded(inspector) =>
        update { implicit session =>
          SQL(delMember).bind(id, inspector.id).execute.apply()
          SQL(insMember).bind(id, 3, inspector.id, inspector.name).update.apply()
        }
      case AuthorRemoved(author) =>
        update { implicit session =>
          SQL(delMember).bind(id, author.id).execute.apply()
        }
      case InspectorRemoved(inspector) =>
        update { implicit session =>
          SQL(delMember).bind(id, inspector.id).execute.apply()
        }
      case GoneForReview =>
        changeStatus(State.Review)
      case GoneComposing =>
        changeStatus(State.Composing)
      case GoneReleased =>
        changeStatus(State.Released)
      case GotObsolete =>
        update { implicit session =>
          sql"update quiz set obsolete=? where id=?".bind(true, id).update.apply()
        }

      case _ =>

  private def changeStatus(state: State)(using id: QuizID, session: ScalikeJdbcSession) = session
    .db
    .withinTx { implicit session =>
      sql"update quiz set status=? where id=?".bind(state.toString, id).update.apply()
    }
