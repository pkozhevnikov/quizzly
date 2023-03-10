package quizzly.author

import scalikejdbc.*

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

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

  def getList()(using ExecutionContext) = Future {
    val list = NamedDB(poolName).readOnly { implicit session =>
      sql"""select q.id,q.title,q.status,q.obsolete,m.role,m.person_id,m.name
              from quiz q right join member m on q.id=m.id order by q.id,m.role,m.name"""
        .map { rs =>
          Row(
            rs.string("id"),
            rs.string("title"),
            rs.string("status"),
            rs.boolean("obsolete"),
            rs.int("role"),
            rs.string("person_id"),
            rs.string("name")
          )
        }
        .list
        .apply()
    }
    list
      .groupBy(_.id)
      .map { (id, rows) =>
        val members = rows.groupMap(_.role)(r => Person(r.person_id, r.name))

        QuizListed(
          id,
          rows.head.title,
          rows.head.obsolete,
          members(1).head,
          members(2).toSet,
          members(3).toSet,
          rows.head.status match
            case "Composing" =>
              Quiz.State.Composing
            case "Review" =>
              Quiz.State.Review
            case "Released" =>
              Quiz.State.Released
            case _ =>
              Quiz.State.Released
        )
      }
      .toList
  }
