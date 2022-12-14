package quizzly.author

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

object LocalProjectionHandlerSpec:
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
      url = "jdbc:h2:mem:readside"
      migrations-table = "schemahistory"
      migrations-locations = ["classpath:db"]
      migration = on
    }
  """).resolve

class LocalProjectionHandlerSpec
    extends wordspec.AnyWordSpec,
      matchers.should.Matchers,
      BeforeAndAfterAll:

  val testKit = ActorTestKit("localprojtest", LocalProjectionHandlerSpec.config)
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
  def nextEvent(id: QuizID, event: Quiz.Event) =
    val s = seq()
    val time = java.time.Instant.now.toEpochMilli
    EventEnvelope(
      akka.persistence.query.Offset.sequence(s),
      s"${QuizEntity.EntityKey.name}|$id",
      s,
      event,
      time
    )

  def changeDb = afterWord("change db on event:")

  import testdata.*

  "LocalProjection" should
    changeDb {

      case class QuizRow(id: String, title: String, status: String, obsolete: Boolean)
      val toQuizRow: WrappedResultSet => QuizRow =
        rs =>
          QuizRow(rs.string("id"), rs.string("title"), rs.string("status"), rs.boolean("obsolete"))
      case class MemberRow(id: String, role: Int, person_id: String, name: String)
      val toMemberRow: WrappedResultSet => MemberRow =
        rs => MemberRow(rs.string("id"), rs.int("role"), rs.string("person_id"), rs.string("name"))

      def proj(id: QuizID, events: Quiz.Event*) =
        val provider = TestSourceProvider(Source(events.map(nextEvent(id, _))), _.offset)
        JdbcProjection.exactlyOnce(
          ProjectionId("testlocal", Quiz.Tags.Single),
          provider,
          () => ScalikeJdbcSession(),
          () => LocalProjectionHandler()
        )

      val created = Quiz.Created("q1", title, intro, curator, authors, inspectors, lenMins)
      "quiz creation" in {
        val projection = proj("q1", created)
        projTestKit.run(projection) {
          val quiz = DB.readOnly { implicit session =>
            sql"select * from quiz where id='q1'".map(toQuizRow).single.apply()
          }
          quiz shouldBe Some(QuizRow("q1", title, "Composing", false))
          val members = DB.readOnly { implicit session =>
            sql"select * from member where id='q1'".map(toMemberRow).list.apply()
          }
          members should contain theSameElementsAs
            Set(
              MemberRow("q1", 1, "cur", "curator name"),
              MemberRow("q1", 2, "author1", "author1 name"),
              MemberRow("q1", 2, "author2", "author2 name"),
              MemberRow("q1", 3, "inspector1", "inspector1 name"),
              MemberRow("q1", 3, "inspector2", "inspector2 name")
            )
        }
      }

      "quiz update" in {
        val projection = proj("q1", created, Quiz.Updated("new title", "new intro", 73))
        projTestKit.run(projection) {
          val quiz = DB.readOnly { implicit session =>
            sql"select * from quiz where id='q1'".map(toQuizRow).single.apply()
          }
          quiz shouldBe Some(QuizRow("q1", "new title", "Composing", false))
        }
      }

      val selMember = "select * from member where id=? and person_id=?"

      "add author" in {
        val p = proj("q1", created, Quiz.AuthorAdded(author3))
        projTestKit.run(p) {
          val newAuthor = DB.readOnly { implicit session =>
            SQL(selMember).bind("q1", "author3").map(toMemberRow).single.apply()
          }
          newAuthor shouldBe Some(MemberRow("q1", 2, "author3", "author3 name"))
        }
      }

      "remove author" in {
        val p = proj("q1", created, Quiz.AuthorRemoved(author1))
        projTestKit.run(p) {
          val none = DB.readOnly { implicit session =>
            SQL(selMember).bind("q1", "author1").map(toMemberRow).single.apply()
          }
          none shouldBe None
        }
      }

      "add inspector" in {
        val p = proj("q1", created, Quiz.InspectorAdded(inspector3))
        projTestKit.run(p) {
          val newInspector = DB.readOnly { implicit session =>
            SQL(selMember).bind("q1", "inspector3").map(toMemberRow).single.apply()
          }
          newInspector shouldBe Some(MemberRow("q1", 3, "inspector3", "inspector3 name"))
        }
      }

      "remove inspector" in {
        val p = proj("q1", created, Quiz.InspectorRemoved(inspector1))
        projTestKit.run(p) {
          val none = DB.readOnly { implicit session =>
            SQL(selMember).bind("q1", "inspector1").map(toMemberRow).single.apply()
          }
          none shouldBe None
        }
      }

      def checkStatus(status: String) =
        val quiz = DB.readOnly { implicit session =>
          sql"select * from quiz where id='q1'".map(toQuizRow).single.apply()
        }
        quiz shouldBe Some(QuizRow("q1", title, status, false))

      "change state to review" in {
        val p = proj("q1", created, Quiz.GoneForReview)
        projTestKit.run(p) {
          checkStatus("Review")
        }
      }

      "change state to composing" in {
        val p = proj("q1", created, Quiz.GoneForReview, Quiz.GoneComposing)
        projTestKit.run(p) {
          checkStatus("Composing")
        }
      }

      "change state to released" in {
        val p = proj("q1", created, Quiz.GoneForReview, Quiz.GoneReleased)
        projTestKit.run(p) {
          checkStatus("Released")
        }
      }

      "set obsolete" in {
        val p = proj("q1", created, Quiz.GoneForReview, Quiz.GoneReleased, Quiz.GotObsolete)
        projTestKit.run(p) {
          val quiz = DB.readOnly { implicit session =>
            sql"select * from quiz where id='q1'".map(toQuizRow).single.apply()
          }
          quiz shouldBe Some(QuizRow("q1", title, "Released", true))
        }
      }

    }
