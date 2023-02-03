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
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito.*
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
      url = "jdbc:h2:mem:factread"
      migrations-table = "schemahistory"
      migrations-locations = ["classpath:db"]
      migration = on
    }
  """).resolve

class ExamProjectionHandlerSpec
    extends wordspec.AnyWordSpec,
      matchers.should.Matchers,
      MockitoSugar,
      BeforeAndAfterAll:

  val testKit = ActorTestKit("examprojtest", ExamProjectionHandlerSpec.config)
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
  def nextEvent(id: ExamID, event: Exam.Event) =
    val s = seq()
    val time = java.time.Instant.now.toEpochMilli
    EventEnvelope(
      akka.persistence.query.Offset.sequence(s),
      s"${ExamEntity.EntityKey.name}|$id",
      s,
      event,
      time
    )

  val student1 = Student("stud1", "stud1 name")
  val student2 = Student("stud2", "stud2 name")
  val student3 = Student("stud3", "stud3 name")
  val student4 = Student("stud4", "stud4 name")
  val student5 = Student("stud5", "stud5 name")

  val official1 = Official("off1", "off1 name")
  val official2 = Official("off2", "off2 name")
  val official3 = Official("off3", "off3 name")
  val official4 = Official("off4", "off4 name")
  val official5 = Official("off5", "off5 name")

  def changeDb = afterWord("change db on event:")

  import Exam.*

  "ExamProjection" should
    changeDb {

      case class ExamRow(
          id: String,
          quizId: String,
          quizTitle: String,
          hostId: String,
          hostName: String,
          trialLength: Int,
          passingGrade: Int,
          prestartAt: ZonedDateTime,
          startAt: ZonedDateTime,
          endAt: ZonedDateTime,
          state: String,
          cancelledAt: Option[Instant]
      )

      val toExamRow: WrappedResultSet => ExamRow =
        rs =>
          ExamRow(
            rs.string("id"),
            rs.string("quiz_id"),
            rs.string("quiz_title"),
            rs.string("host_id"),
            rs.string("host_name"),
            rs.int("trial_length"),
            rs.int("passing_grade"),
            rs.zonedDateTime("prestart_at").withZoneSameInstant(ZoneId.of("Z")),
            rs.zonedDateTime("start_at").withZoneSameInstant(ZoneId.of("Z")),
            rs.zonedDateTime("end_at").withZoneSameInstant(ZoneId.of("Z")),
            rs.string("state"),
            rs.zonedDateTimeOpt("cancelled_at").map(_.toInstant)
          )
      val toPerson: WrappedResultSet => Person =
        rs => Person.of(rs.string("testee_place"))(rs.string("testee_id"), rs.string("testee_name"))

      import quizzly.trial.{grpc => trial}

      val trialRegistryClient = mock[trial.Registry]

      def proj(id: ExamID, events: Exam.Event*) =
        val provider = TestSourceProvider(Source(events.map(nextEvent(id, _))), _.offset)
        JdbcProjection.exactlyOnce(
          ProjectionId("testlocal", Exam.Tags.Single),
          provider,
          () => ScalikeJdbcSession(),
          () => ExamProjectionHandler(trialRegistryClient)
        )

      def getExam(id: String) = DB.readOnly { implicit session =>
        sql"select * from exam where id=?".bind(id).map(toExamRow).single.apply()
      }
      def getTestees(id: String) = DB.readOnly { implicit session =>
        sql"select * from testee where exam_id=? order by testee_id"
          .bind(id)
          .map(toPerson)
          .list
          .apply()
      }

      val created = Created(
        Quiz("q1", "quiz #1"),
        45,
        ZonedDateTime.parse("2023-01-03T10:00:00Z"),
        ExamPeriod(
          ZonedDateTime.parse("2023-01-05T10:00:00Z"),
          ZonedDateTime.parse("2023-01-06T10:00:00Z")
        ),
        Set(student1, student2),
        official1,
        60
      )

      val initExamRow = ExamRow(
        "e1",
        "q1",
        "quiz #1",
        "off1",
        "off1 name",
        45,
        60,
        ZonedDateTime.parse("2023-01-03T10:00:00Z"),
        ZonedDateTime.parse("2023-01-05T10:00:00Z"),
        ZonedDateTime.parse("2023-01-06T10:00:00Z"),
        "Pending",
        None
      )

      "created" in {
        val p = proj("e1", created)

        projTestKit.run(p) {
          getExam("e1") shouldBe Some(initExamRow)
          getTestees("e1") shouldBe List(student1, student2)
        }
      }

      "testees included" in {
        val p = proj("e1", created, TesteesIncluded(Set(official2, student3)))
        projTestKit.run(p) {
          getTestees("e1") shouldBe List(official2, student1, student2, student3)
        }
      }

      "testees excluded" in {
        val p = proj("e1", created, TesteesExcluded(Set(student1, student2)))
        projTestKit.run(p) {
          getTestees("e1") shouldBe List.empty[Person]
        }
      }

      "trial attrs set" in {
        val p = proj("e1", created, TrialAttrsSet(73, 83))
        projTestKit.run(p) {
          getExam("e1") shouldBe Some(initExamRow.copy(trialLength = 73, passingGrade = 83))
        }
      }

      "transit to upcoming" in {
        val p = proj("e1", created, GoneUpcoming)
        projTestKit.run(p) {
          getExam("e1") shouldBe Some(initExamRow.copy(state = "Upcoming"))
        }
      }

      "transit to in progress" in {
        reset(trialRegistryClient)
        val p = proj("e1", created, GoneUpcoming, GoneInProgress)
        projTestKit.run(p) {
          getExam("e1") shouldBe Some(initExamRow.copy(state = "InProgress"))
          verify(trialRegistryClient).registerExam(trial.RegisterExamRequest(
            "e1", "q1", 45, 
            Instant.parse("2023-01-05T10:00:00Z").getEpochSecond,
            Instant.parse("2023-01-06T10:00:00Z").getEpochSecond,
            Seq(trial.Person("stud1", "stud1 name"), trial.Person("stud2", "stud2 name"))
          ))
        }
      }

      "transit to ended" in {
        val p = proj("e1", created, GoneUpcoming, GoneInProgress, GoneEnded)
        projTestKit.run(p) {
          getExam("e1") shouldBe Some(initExamRow.copy(state = "Ended"))
        }
      }

      "cancelled" in {
        val now = Instant.now()
        val p = proj("e1", created, GoneCancelled(now))
        projTestKit.run(p) {
          getExam("e1") shouldBe
            Some(initExamRow.copy(state = "Cancelled", cancelledAt = Some(now)))
        }
      }
    }
