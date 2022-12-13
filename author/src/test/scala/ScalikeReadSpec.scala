package quizzly.author

import org.scalatest.*
import scalikejdbc.*

import akka.actor.testkit.typed.scaladsl.ActorTestKit

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

  import testdata.*

  val q1 = QuizListed("1", "quiz 1", false, curator, authors, inspectors, Quiz.State.Composing)
  val q2 = QuizListed(
    "2",
    "quiz 2",
    false,
    curator,
    authors - author1,
    inspectors + inspector3,
    Quiz.State.Review
  )
  val q3 = QuizListed(
    "3",
    "quiz 3",
    true,
    author3,
    authors + curator,
    inspectors - inspector2,
    Quiz.State.Released
  )

  def DB = NamedDB(testKit.system.name)

  override def beforeAll() =
    super.beforeAll()
    ScalikeJdbcSetup(testKit.system)

    NamedDB(testKit.system.name).localTx { implicit session =>
      val insmemb = "insert into member (id,role,person_id,name) values (?,?,?,?)"
      def ins(q: QuizListed) =
        sql"insert into quiz (id,title,status,obsolete) values (?,?,?,?)"
          .bind(q.id, q.title, q.state.toString, q.obsolete)
          .update
          .apply()
        SQL(insmemb).bind(q.id, 1, q.curator.id, q.curator.name).update.apply()
        q.authors
          .foreach { a =>
            SQL(insmemb).bind(q.id, 2, a.id, a.name).update.apply()
          }
        q.inspectors
          .foreach { i =>
            SQL(insmemb).bind(q.id, 3, i.id, i.name).update.apply()
          }
      ins(q1)
      ins(q2)
      ins(q3)
    }

  override def afterAll() =
    super.afterAll()
    testKit.shutdownTestKit()

  "read side" should {
    "return list of quizzes" in {
      ScalikeRead(testKit.system.name)
        .getList()
        .map { list =>
          list should contain inOrder (q1, q2, q3)
        }
    }
  }
