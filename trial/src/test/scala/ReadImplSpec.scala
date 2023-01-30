package quizzly.trial

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import org.scalatest.*
import scalikejdbc.*

import java.time.*

object ReadImplSpec:
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
      url = "jdbc:h2:mem:trialread"
      migrations-table = "schemahistory"
      migrations-locations = ["classpath:db"]
      migration = on
    }
  """).resolve

class ReadImplSpec extends wordspec.AsyncWordSpec, BeforeAndAfterAll, matchers.should.Matchers:

  val testKit = ActorTestKit("trialreadtest", ReadImplSpec.config)

  val e1 = ExamListed(
    "e1",
    "q1",
    "q1 title",
    Instant.parse("2023-01-28T10:00:00Z"),
    Instant.parse("2023-01-30T10:00:00Z"),
    45
  )
  val e2 = ExamListed(
    "e2",
    "q2",
    "q2 title",
    Instant.parse("2023-02-01T10:00:00Z"),
    Instant.parse("2023-02-05T10:00:00Z"),
    60
  )

  def DB = NamedDB(testKit.system.name)

  override def beforeAll() =
    super.beforeAll()
    ScalikeJdbcSetup(testKit.system)

    NamedDB(testKit.system.name).localTx { implicit session =>
      val insq = "insert into quiz (id,title,intro) values (?,?,?)"
      val inse = "insert into exam (id,quiz_id,start_at,end_at,trial_length) values (?,?,?,?,?)"
      SQL(insq).bind("q1", "q1 title", "q1 intro").update.apply()
      SQL(insq).bind("q2", "q2 title", "q2 intro").update.apply()
      def insexam(e: ExamListed) = SQL(inse)
        .bind(e.id, e.quizId, e.start, e.end, e.trialLength)
        .update
        .apply()
      insexam(e1)
      insexam(e2)
    }

  override def afterAll() =
    super.afterAll()
    testKit.shutdownTestKit()

  "read side" should {
    "return list of exams" in {
      ReadImpl(testKit.system.name)
        .examList()
        .map { list =>
          list should contain inOrder (e2, e1)
        }
    }

    "return exam info" in {
      ReadImpl(testKit.system.name)
        .examInfo("e2")
        .map { exam =>
          exam shouldBe
            ExamInfo(
              "q2",
              "q2 title",
              "q2 intro",
              "e2",
              Instant.parse("2023-02-01T10:00:00Z"),
              Instant.parse("2023-02-05T10:00:00Z"),
              60
            )
        }
    }

    "exam info failed if exam not exists" in {
      val exFut = recoverToExceptionIf[java.util.NoSuchElementException] {
        ReadImpl(testKit.system.name).examInfo("x")
      }
      exFut.map { ex =>
        ex.getMessage shouldBe "exam [x] not found"
      }
    }

  }
