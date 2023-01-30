package quizzly.trial

import scalikejdbc.*

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import java.time.ZoneId

class ReadImpl(poolName: String) extends Read:

  def examList()(using ExecutionContext) = Future {
    NamedDB(poolName).readOnly { implicit session =>
      sql"select e.*,q.title from exam e join quiz q on e.quiz_id=q.id order by start_at desc"
        .map { rs =>
          ExamListed(
            rs.string("id"),
            rs.string("quiz_id"),
            rs.string("title"),
            rs.zonedDateTime("start_at").withZoneSameInstant(ZoneId.of("Z")).toInstant,
            rs.zonedDateTime("end_at").withZoneSameInstant(ZoneId.of("Z")).toInstant,
            rs.int("trial_length")
          )
        }
        .list
        .apply()
    }
  }

  def examInfo(id: ExamID)(using ExecutionContext) = Future {
    val res = NamedDB(poolName).readOnly { implicit session =>
      sql"""select e.*,q.title,q.intro from exam e
              join quiz q on e.quiz_id=q.id
              where e.id=?
              limit 1""".bind(id).map { rs =>
                ExamInfo(
                  rs.string("quiz_id"),
                  rs.string("title"),
                  rs.string("intro"),
                  rs.string("id"),
                  rs.zonedDateTime("start_at").withZoneSameInstant(ZoneId.of("Z")).toInstant,
                  rs.zonedDateTime("end_at").withZoneSameInstant(ZoneId.of("Z")).toInstant,
                  rs.int("trial_length")
                )
              }
              .single
              .apply()
    }
    res match
      case Some(info) =>
        info
      case None =>
        throw java.util.NoSuchElementException(s"exam [$id] not found")
  }
