package quizzly.school

import scalikejdbc.*

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import java.time.ZoneId

class ScalikeRead(poolName: String) extends Read:

  case class Row(
      id: String,
      title: String,
      status: String,
      obsolete: Boolean,
      role: Int,
      person_id: String,
      name: String
  )

  def quizList()(using ExecutionContext) = Future {
    NamedDB(poolName).readOnly { implicit session =>
      sql"select * from quizfact order by id"
        .map { rs =>
          QuizListed(
            rs.string("id"),
            rs.string("title"),
            rs.boolean("obsolete"),
            rs.boolean("in_use"),
            rs.boolean("is_published"),
            rs.boolean("ever_published")
          )
        }
        .list
        .apply()
    }
  }

  def examList()(using ExecutionContext) = Future {
    NamedDB(poolName).readOnly { implicit session =>
      sql"select * from exam order by start_at"
        .map { rs =>
          ExamView(
            rs.string("id"),
            QuizRef(rs.string("quiz_id"), rs.string("quiz_title")),
            ExamPeriod(
              rs.zonedDateTime("start_at").withZoneSameInstant(ZoneId.of("Z")),
              rs.zonedDateTime("end_at").withZoneSameInstant(ZoneId.of("Z"))
            ),
            Official(rs.string("host_id"), rs.string("host_name")),
            rs.string("state"),
            rs.zonedDateTimeOpt("cancelled_at").map(_.withZoneSameInstant(ZoneId.of("Z")).toInstant),
            rs.int("trial_length"),
            rs.zonedDateTime("prestart_at").withZoneSameInstant(ZoneId.of("Z"))
          )
        }
        .list
        .apply()
    }
  }

  def testees(id: ExamID)(using ExecutionContext) = Future {
    NamedDB(poolName).readOnly { implicit session =>
      sql"select * from testee where exam_id=? order by testee_name".bind(id)
        .map(rs => Person.of(rs.string("testee_place"))(rs.string("testee_id"), rs.string("testee_name")))
        .list
        .apply()
    }
  }

