package quizzly.trial

import akka.projection.*
import scalikejdbc.*

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import eventsourced.EventEnvelope
import jdbc.scaladsl.JdbcHandler

class ExamProjectionHandler extends JdbcHandler[EventEnvelope[ExamEntity.Event], ScalikeJdbcSession]:

  import ExamEntity.*

  override final def process(session: ScalikeJdbcSession, envelope: EventEnvelope[ExamEntity.Event]) =
    val id = envelope.persistenceId.split("\\|")(1)
    given ScalikeJdbcSession = session
    given ExamID = id
    def update[Result](expr: DBSession => Result) = session.db.withinTx(expr(_))
    envelope.event match

      case c: Registered =>
        update { implicit session =>
          sql"insert into exam (id,quiz_id,start_at,end_at,trial_length) values (?,?,?,?,?)"
            .bind(
              id,
              c.quiz,
              c.period.start,
              c.period.end,
              c.trialLength
            )
            .update
            .apply()
        }

      case Unregistered =>
        update { implicit session =>
          sql"delete from exam where id=?".bind(id).update.apply()
        }
