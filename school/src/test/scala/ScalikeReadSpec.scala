package quizzly.school

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import org.scalatest.*
import scalikejdbc.*

import java.time.*

object ScalikeReadSpec:
  import com.typesafe.config.ConfigFactory
  val config =
    ConfigFactory
      .parseString("""
    jdbc-connection-settings {
      connection-pool {
        timeout = 250ms
        max-pool-size = 10
      }
      driver = "org.h2.Driver"
      user = "sa"
      password = "sa"
      url = "jdbc:h2:mem:read"
      migrations-table = "schemahistory"
      migrations-locations = ["classpath:db"]
      migration = on
    }
  """).resolve

class ScalikeReadSpec extends wordspec.AsyncWordSpec, BeforeAndAfterAll, matchers.should.Matchers:

  val testKit = ActorTestKit("readtest", ScalikeReadSpec.config)

  val dt1 = ZonedDateTime.parse("2023-01-12T10:10:10Z")

  val q1 = QuizListed("q1", "q1 title", true, false, false, false)
  val q2 = QuizListed("q2", "q2 title", false, true, false, false)
  val q3 = QuizListed("q3", "q3 title", false, false, true, false)
  val q4 = QuizListed("q4", "q4 title", false, false, false, true)

  val e1 = ExamView("e1", QuizRef("q1", "q1 title"),
    ExamPeriod(dt1, dt1.plusDays(1)), Official("off1", "off1 name"), "Pending", None,
    45, dt1.minusDays(1))
  val e1testees = Set(Official("off2", "off2 name"), Student("stud1", "stud2 name"))

  val e2 = ExamView("e2", QuizRef("q2", "q2 title"),
    ExamPeriod(dt1.plusDays(1), dt1.plusDays(3)), Official("off3", "off3 name"), "Cancelled",
      Some(dt1.plusDays(2).toInstant), 60, dt1)
  val e2testees = Set(Official("off4", "off4 name"), Student("stud2", "stud2 name"))

  def DB = NamedDB(testKit.system.name)

  override def beforeAll() =
    super.beforeAll()
    ScalikeJdbcSetup(testKit.system)

    NamedDB(testKit.system.name).localTx { implicit session =>
      val insquiz = """insert into quizfact (id,title,obsolete,in_use,is_published,ever_published)
        values (?,?,?,?,?,?)"""
      val insexam = """insert into exam (id,quiz_id,quiz_title,host_id,host_name,start_at,end_at,
        state,cancelled_at,trial_length,prestart_at) values (?,?,?,?,?,?,?,?,?,?,?)"""
      val instste = """insert into testee (exam_id,testee_id,testee_name,testee_place)
        values (?,?,?,?)"""
      def insq(q: QuizListed) =
        SQL(insquiz).bind(q.id, q.title, q.obsolete, q.inUse, q.isPublished, q.everPublished)
          .update.apply()
      insq(q1)
      insq(q2)
      insq(q3)
      insq(q4)
      def inse(e: ExamView) =
        SQL(insexam).bind(e.id, e.quiz.id, e.quiz.title, e.host.id, e.host.name, 
          e.period.start, e.period.end, e.state, e.cancelledAt, e.trialLength, e.prestartAt)
          .update.apply()
      inse(e1)
      inse(e2)
      def inst(examId: String, ts: Set[Person]) = ts.foreach { p =>
        SQL(instste).bind(examId, p.id, p.name, p.place).update.apply()
      }
      inst("e1", e1testees)
      inst("e2", e2testees)
    }

  override def afterAll() =
    super.afterAll()
    testKit.shutdownTestKit()

  "read side" should {
    "return list of quizzes" in {
      ScalikeRead(testKit.system.name)
        .quizList()
        .map { list =>
          list should contain inOrder (q1, q2, q3)
        }
    }
    "return list of exams" in {
      ScalikeRead(testKit.system.name)
        .examList()
        .map { list =>
          list should contain inOrder(e1, e2)
        }
    }
    "return list of testees" in {
      val read = ScalikeRead(testKit.system.name)
      read.testees("e1").map { list =>
        list should contain theSameElementsAs e1testees
      }
      read.testees("e2").map { list =>
        list should contain theSameElementsAs e2testees
      }
    }
  }
