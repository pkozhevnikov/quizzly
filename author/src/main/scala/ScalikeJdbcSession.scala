package quizzly.author

import akka.actor.typed.ActorSystem
import akka.japi.function.Function
import akka.projection.jdbc.JdbcSession
import scalikejdbc.*

import java.sql.Connection

object ScalikeJdbcSession:
  def withSession[R](f: ScalikeJdbcSession => R)(using sys: ActorSystem[?]): R =
    val session = new ScalikeJdbcSession()
    try f(session)
    finally session.close()

final class ScalikeJdbcSession(using sys: ActorSystem[?]) extends JdbcSession:
  val db: DB = DB(ConnectionPool(sys.name).borrow())
  db.autoClose(false)

  override def withConnection[Result](func: Function[Connection, Result]): Result =
    db.begin()
    db.withinTxWithConnection(func(_))

  override def commit(): Unit = db.commit()

  override def rollback(): Unit = db.rollback()

  override def close(): Unit = db.close()
