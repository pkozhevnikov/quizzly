package quizzly.school

import akka.projection.*
import scalikejdbc.*

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import eventsourced.EventEnvelope
import jdbc.scaladsl.JdbcHandler

import quizzly.trial.{grpc => trial}

class ExamProjectionHandler(trialRegistryClient: trial.Registry)
    extends JdbcHandler[EventEnvelope[Exam.Event], ScalikeJdbcSession]:

  import Exam.*

  override final def process(session: ScalikeJdbcSession, envelope: EventEnvelope[Exam.Event]) =
    val id = envelope.persistenceId.split("\\|")(1)
    given ScalikeJdbcSession = session
    given ExamID = id
    def update[Result](expr: DBSession => Result) = session.db.withinTx(expr(_))
    envelope.event match

      case c: Created =>
        update { implicit session =>
          sql"delete from exam where id=?".bind(id).update.apply()
          sql"""insert into exam (id,quiz_id,quiz_title,host_id,host_name,trial_length,prestart_at,
            start_at,end_at,state,passing_grade) values (?,?,?,?,?,?,?,?,?,?,?)"""
            .bind(
              id,
              c.quiz.id,
              c.quiz.title,
              c.host.id,
              c.host.name,
              c.trialLengthMinutes,
              c.preparationStart,
              c.period.start,
              c.period.end,
              State.Pending.toString,
              c.passingGrade
            )
            .update
            .apply()
          c.testees
            .foreach { person =>
              sql"insert into testee (exam_id,testee_id,testee_name,testee_place) values (?,?,?,?)"
                .bind(id, person.id, person.name, person.place)
                .update
                .apply()
            }
        }
      case TesteesIncluded(testees) =>
        update { implicit session =>
          testees.foreach { person =>
            sql"insert into testee (exam_id,testee_id,testee_name,testee_place) values (?,?,?,?)"
              .bind(id, person.id, person.name, person.place)
              .update
              .apply()
          }
        }
      case TesteesExcluded(testees) =>
        update { implicit session =>
          testees.foreach { person =>
            sql"delete from testee where exam_id=? and testee_id=?"
              .bind(id, person.id)
              .update
              .apply()
          }
        }
      case TrialAttrsSet(length, grade) =>
        update { implicit session =>
          sql"update exam set trial_length=?,passing_grade=? where id=?"
            .bind(length, grade, id)
            .update
            .apply()
        }
      case GoneUpcoming =>
        updateState(State.Upcoming)
      case GoneInProgress =>
        updateState(State.InProgress)
        session
          .db
          .withinTx { implicit session =>
            val testees =
              sql"select testee_id,testee_name from testee where exam_id=?"
                .bind(id)
                .map(r => trial.Person(r.string("testee_id"), r.string("testee_name")))
                .list
                .apply()
                .toSeq
            sql"select * from exam where id=?"
              .bind(id)
              .map(r =>
                trial.RegisterExamRequest(
                  id,
                  r.string("quiz_id"),
                  r.int("trial_length"),
                  r.timestamp("start_at").toInstant.getEpochSecond,
                  r.timestamp("end_at").toInstant.getEpochSecond,
                  testees
                )
              )
              .single
              .apply()
          } match
          case Some(req) =>
            trialRegistryClient.registerExam(req)
          case _ =>
      case GoneEnded =>
        updateState(State.Ended)
      case GoneCancelled(at) =>
        update { implicit session =>
          sql"update exam set state=?,cancelled_at=? where id=?"
            .bind(State.Cancelled.toString, at, id)
            .update
            .apply()
        }
      case _ =>

  def updateState(state: State)(using id: ExamID, session: ScalikeJdbcSession) = session
    .db
    .withinTx { implicit session =>
      sql"update exam set state=? where id=?".bind(state.toString, id).update.apply()
    }
