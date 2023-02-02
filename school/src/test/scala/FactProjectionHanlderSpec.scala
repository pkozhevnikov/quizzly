package quizzly.school

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

object FactProjectionHandlerSpec:
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
      url = "jdbc:h2:mem:factread"
      migrations-table = "schemahistory"
      migrations-locations = ["classpath:db"]
      migration = on
    }
  """).resolve

class FactProjectionHandlerSpec
    extends wordspec.AnyWordSpec,
      matchers.should.Matchers,
      BeforeAndAfterAll:

  val testKit = ActorTestKit("factprojtest", FactProjectionHandlerSpec.config)
  val projTestKit = ProjectionTestKit(testKit.system)
  given ActorSystem[?] = testKit.system
  given ExecutionContext = testKit.system.executionContext

  def DB = NamedDB(testKit.system.name)

  override def beforeAll() =
    ScalikeJdbcSetup(testKit.system)
    JdbcProjection.createTablesIfNotExists(() => ScalikeJdbcSession())

  override def afterAll() = testKit.shutdownTestKit()

  var _seq = -1
  def seq() =
    _seq = _seq + 1
    _seq
  def nextEvent(id: QuizID, event: QuizFact.Event) =
    val s = seq()
    val time = java.time.Instant.now.toEpochMilli
    EventEnvelope(
      akka.persistence.query.Offset.sequence(s),
      s"${QuizFact.EntityKey.name}|$id",
      s,
      event,
      time
    )

  def changeDb = afterWord("change db on event:")

  "FactProjection" should
    changeDb {

      case class QuizRow(
          id: String,
          title: String,
          obsolete: Boolean,
          inUse: Boolean,
          everPublished: Boolean,
          isPublished: Boolean,
          length: Int
      )
      val toQuizRow: WrappedResultSet => QuizRow =
        rs =>
          QuizRow(
            rs.string("id"),
            rs.string("title"),
            rs.boolean("obsolete"),
            rs.boolean("in_use"),
            rs.boolean("ever_published"),
            rs.boolean("is_published"),
            rs.int("recommended_length")
          )

      def proj(id: QuizID, events: QuizFact.Event*) =
        val provider = TestSourceProvider(Source(events.map(nextEvent(id, _))), _.offset)
        JdbcProjection.exactlyOnce(
          ProjectionId("testlocal", QuizFact.Tags.Single),
          provider,
          () => ScalikeJdbcSession(),
          () => FactProjectionHandler()
        )

      val full = FullQuiz(
        "q1",
        "new quiz",
        "quiz1 intro",
        45,
        PersonRef("", ""),
        Set.empty,
        Set.empty,
        List.empty
      )

      val inited = QuizFact.Inited(full)

      def getFact(id: String) = DB.readOnly { implicit session =>
        sql"select * from quizfact where id=?".bind(id).map(toQuizRow).single.apply()
      }

      "fact initialization" in {
        val projection = proj("q1", inited)
        projTestKit.run(projection) {
          getFact("q1") shouldBe Some(QuizRow("q1", "new quiz", false, false, false, false, 45))
        }
      }

      "got obsolete" in {
        val p = proj("q2", inited, QuizFact.GotObsolete)
        projTestKit.run(p) {
          getFact("q2") shouldBe Some(QuizRow("q2", "new quiz", true, false, false, false, 45))
        }
      }

      "published" in {
        val p = proj("q3", inited, QuizFact.Published)
        projTestKit.run(p) {
          getFact("q3") shouldBe Some(QuizRow("q3", "new quiz", false, false, true, true, 45))
        }
      }

      "unpublisjed" in {
        val p = proj("q4", inited, QuizFact.Published, QuizFact.Unpublished)
        projTestKit.run(p) {
          getFact("q4") shouldBe Some(QuizRow("q4", "new quiz", false, false, true, false, 45))
        }
      }

      "used" in {
        val p = proj("q5", inited, QuizFact.Used("any"))
        projTestKit.run(p) {
          getFact("q5") shouldBe Some(QuizRow("q5", "new quiz", false, true, false, false, 45))
        }
      }

      "stop usage" in {
        val p = proj("q6", inited, QuizFact.Used("any"), QuizFact.GotUnused)
        projTestKit.run(p) {
          getFact("q6") shouldBe Some(QuizRow("q6", "new quiz", false, false, false, false, 45))
        }
      }

    }
