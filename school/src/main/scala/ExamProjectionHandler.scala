package quizzly.school

import akka.projection.*
import scalikejdbc.*

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import eventsourced.EventEnvelope
import jdbc.scaladsl.JdbcHandler

class ExamProjectionHandler extends JdbcHandler[EventEnvelope[Exam.Event], ScalikeJdbcSession]:

  import Exam.*

  val insMember = "insert into member (id,role,person_id,name) values (?,?,?,?)"
  val delMember = "delete from member where id=? and person_id=?"

  override final def process(session: ScalikeJdbcSession, envelope: EventEnvelope[Exam.Event]) =
    val id = envelope.persistenceId.split("\\|")(1)
    given ScalikeJdbcSession = session
    given ExamID = id
    def update[Result](expr: DBSession => Result) = session.db.withinTx(expr(_))
    envelope.event match

      case c: Created => 
        update { implicit session =>
          sql"delete from testee where exam_id=?".bind(id).update.apply()
          sql"""insert into exam (id,quiz_id,quiz_title,host_id,host_name,trial_length,prestart_at,
            start_at,end_at,state) values (?,?,?,?,?,?,?,?,?,?)"""
              .bind(id, c.quiz.id, c.quiz.title, c.host.id, c.host.name, c.trialLengthMinutes,
                c.preparationStart, c.period.start, c.period.end, State.Pending.toString)
              .update
              .apply()
          c.testees.foreach { person =>
            sql"insert into testee (exam_id,testee_id,testee_name,testee_place) values (?,?,?,?)"
              .bind(id, person.id, person.name, person.place)
              .update
              .apply()
          }
        }

      case _ =>
