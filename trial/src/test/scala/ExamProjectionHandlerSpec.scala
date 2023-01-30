package quizzly.trial

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.*
import akka.projection.ProjectionBehavior
import akka.projection.ProjectionId
import akka.projection.eventsourced.EventEnvelope
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.testkit.scaladsl.ProjectionTestKit
import akka.projection.testkit.scaladsl.TestSourceProvider
import akka.stream.scaladsl.*
import org.scalatest.*
import scalikejdbc.*

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import java.time.*

object ExamProjectionHandlerSpec:
  import com.typesafe.config.ConfigFactory
  val config =
    ConfigFactory
      .parseString("""
    akka {
      projection {
        jdbc.blocking-jdbc-dispatcher.thread-pool-executor.fixed-pool-size = 10
        jdbc.dialect = h2-dialect
      }
    }
    jdbc-connection-settings {
      connection-pool {
        timeout = 250ms
        max-pool-size = ${akka.projection.jdbc.blocking-jdbc-dispatcher.thread-pool-executor.fixed-pool-size}
      }
      driver = "org.h2.Driver"
      user = "sa"
      password = "sa"
      url = "jdbc:h2:mem:trialprojread"
      migrations-table = "schemahistory"
      migrations-locations = ["classpath:db"]
      migration = on
    }
  """).resolve

class ExamProjectionHandlerSpec
    extends wordspec.AnyWordSpec,
      matchers.should.Matchers,
      BeforeAndAfterAll:

  val testKit = ActorTestKit("examprojtest", ExamProjectionHandlerSpec.config)
  val projTestKit = ProjectionTestKit(testKit.system)
  given ActorSystem[?] = testKit.system
  given ExecutionContext = testKit.system.executionContext

  override def beforeAll() =
    ScalikeJdbcSetup(testKit.system)
    DB.localTx { implicit session =>
      sql"insert into quiz (id) values ('q1')".update.apply()
    }
    JdbcProjection.createTablesIfNotExists(() => ScalikeJdbcSession())

  override def afterAll() = testKit.shutdownTestKit()

  def DB = NamedDB(testKit.system.name)

  var _seq = -1
  def seq() =
    _seq = _seq + 1
    _seq
  def nextEvent(id: ExamID, event: ExamEntity.Event) =
    val s = seq()
    val time = java.time.Instant.now.toEpochMilli
    EventEnvelope(
      akka.persistence.query.Offset.sequence(s),
      s"${ExamEntity.EntityKey.name}|$id",
      s,
      event,
      time
    )

  def changeDb = afterWord("change db on event:")

  "ExamProjection" should
    changeDb {

      def proj(id: ExamID, events: ExamEntity.Event*) =
        val provider = TestSourceProvider(Source(events.map(nextEvent(id, _))), _.offset)
        JdbcProjection.exactlyOnce(
          ProjectionId("testlocal", ExamEntity.Tags.Single),
          provider,
          () => ScalikeJdbcSession(),
          () => ExamProjectionHandler()
        )

      val registered = ExamEntity.Registered(
        "q1",
        ExamPeriod(Instant.parse("2023-01-28T10:00:00Z"), Instant.parse("2023-01-30T10:00:00Z")),
        45,
        Set()
      )

      case class Row(id: String, quizId: String, start: Instant, end: Instant, trialLength: Int)

      "registered" in {
        val p = proj("e1", registered)

        projTestKit.run(p) {
          val row = NamedDB(testKit.system.name).readOnly { implicit session =>
            sql"select * from exam where id='e1'"
              .map { r =>
                Row(
                  r.string("id"),
                  r.string("quiz_id"),
                  r.zonedDateTime("start_at").withZoneSameInstant(ZoneId.of("Z")).toInstant,
                  r.zonedDateTime("end_at").withZoneSameInstant(ZoneId.of("Z")).toInstant,
                  r.int("trial_length")
                )
              }
              .single
              .apply()
          }
          row shouldBe defined
          row.get shouldBe
            Row(
              "e1",
              "q1",
              Instant.parse("2023-01-28T10:00:00Z"),
              Instant.parse("2023-01-30T10:00:00Z"),
              45
            )
        }
      }

      "unregistered" in {
        val p = proj("e2", registered, ExamEntity.Unregistered)
        projTestKit.run(p) {
          val row = NamedDB(testKit.system.name).readOnly { implicit session =>
            sql"select * from exam where id='e2'".map(_.string("id")).single.apply()
          }
          row shouldBe None
        }
      }

    }
