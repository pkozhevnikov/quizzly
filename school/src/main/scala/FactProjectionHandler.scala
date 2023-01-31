package quizzly.school

import akka.projection.*
import scalikejdbc.*

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import eventsourced.EventEnvelope
import jdbc.scaladsl.JdbcHandler

class FactProjectionHandler extends JdbcHandler[EventEnvelope[QuizFact.Event], ScalikeJdbcSession]:

  import QuizFact.*

  override final def process(session: ScalikeJdbcSession, envelope: EventEnvelope[QuizFact.Event]) =
    val id = envelope.persistenceId.split("\\|")(1)
    given ScalikeJdbcSession = session
    given QuizID = id
    def update[Result](expr: DBSession => Result) = session.db.withinTx(expr(_))
    envelope.event match
      case Inited(title, obsolete, length) =>
        update { implicit session =>
          sql"""insert into quizfact (
              id,title,obsolete,in_use,ever_published,is_published,recommended_length
            ) values (?,?,?,?,?,?,?)"""
            .bind(id, title, obsolete, false, false, false, length)
            .update
            .apply()
        }
      case GotObsolete =>
        update { implicit session =>
          sql"update quizfact set obsolete=true where id=?".bind(id).update.apply()
        }
      case Published =>
        update { implicit session =>
          sql"update quizfact set ever_published=true,is_published=true where id=?"
            .bind(id)
            .update
            .apply()
        }
      case Unpublished =>
        update { implicit session =>
          sql"update quizfact set is_published=false where id=?".bind(id).update.apply()
        }
      case Used(_) =>
        update { implicit session =>
          sql"update quizfact set in_use=true where id=?".bind(id).update.apply()
        }
      case GotUnused =>
        update { implicit session =>
          sql"update quizfact set in_use=false where id=?".bind(id).update.apply()
        }

      case _ =>
