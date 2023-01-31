package quizzly.trial

import scala.concurrent.*
import scala.concurrent.duration.*
import scala.util.*

import akka.serialization.jackson.JacksonObjectMapperProvider
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import org.scalatest.*
import scalikejdbc.*

import java.time.*

object RegistryImplSpec:
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
      url = "jdbc:h2:mem:registry"
      migrations-table = "schemahistory"
      migrations-locations = ["classpath:db"]
      migration = on
    }
  """).resolve

class RegistryImplSpec extends wordspec.AnyWordSpec, BeforeAndAfterAll, matchers.should.Matchers:

  val testKit = ActorTestKit("regtest", RegistryImplSpec.config)

  given ActorSystem[?] = testKit.system

  def DB = NamedDB(testKit.system.name)

  override def beforeAll() =
    super.beforeAll()
    ScalikeJdbcSetup(testKit.system)

  override def afterAll() =
    super.afterAll()
    testKit.shutdownTestKit()

  "registry" should {

    "register quiz" in {
      val probe = testKit.createTestProbe[ExamEntity.Command]()
      val registry = RegistryImpl(_ => probe.ref)
      val res = registry.registerQuiz(
        grpc.RegisterQuizRequest(
          "q1",
          "q1 title",
          "q1 intro",
          Seq(
            grpc.Section(
              "s1",
              "s1 title",
              "s1 intro",
              Seq(
                grpc.Item(
                  "i1",
                  "i1 intro",
                  grpc.Statement("i1 def", Some("i1 def img")),
                  Seq(
                    grpc.Hint(Seq(grpc.Statement("i1 h1 alt1", None))),
                    grpc.Hint(Seq(grpc.Statement("i1 h1 alt2", Some("i1 h1 alt2 img"))))
                  ),
                  true,
                  Seq(1, 2)
                ),
                grpc.Item(
                  "i2",
                  "i2 intro",
                  grpc.Statement("i2 def", None),
                  Seq(grpc.Hint(Seq(grpc.Statement("i2 h1 alt1", None)))),
                  false,
                  Seq(2, 3)
                )
              )
            )
          )
        )
      )
      Await.result(res, 1.second) shouldBe grpc.RegisterQuizResponse.of()
      Await.result(registry.get("q1"), 1.second) shouldBe
        Quiz(
          "q1",
          "q1 title",
          "q1 intro",
          List(
            Section(
              "s1",
              "s1 title",
              "s1 intro",
              List(
                Item(
                  "i1",
                  "i1 intro",
                  Statement("i1 def", Some("i1 def img")),
                  List(
                    List(Statement("i1 h1 alt1", None)),
                    List(Statement("i1 h1 alt2", Some("i1 h1 alt2 img")))
                  ),
                  true,
                  List(1, 2)
                ),
                Item(
                  "i2",
                  "i2 intro",
                  Statement("i2 def", None),
                  List(List(Statement("i2 h1 alt1", None))),
                  false,
                  List(2, 3)
                )
              )
            )
          )
        )

      val row = DB.readOnly { implicit session =>
        sql"select title,intro from quiz where id='q1'"
          .map(rs => (rs.string("title"), rs.string("intro")))
          .single
          .apply()
      }
      row shouldBe defined
      row.get shouldBe ("q1 title", "q1 intro")
    }

    "get failed if quiz not found" in {
      val registry = RegistryImpl(_ => testKit.createTestProbe[ExamEntity.Command]().ref)
      val Some(Failure(ex)) = Await.ready(registry.get("xyz"), 1.second).value
      ex shouldBe a[NoSuchElementException]
      ex.getMessage shouldBe "quiz [xyz] not found"
    }

    "register exam" in {
      val probe = testKit.createTestProbe[ExamEntity.Command]()
      var callId = ""
      val exam =
        (id: String) =>
          callId = id
          probe.ref
      val registry = RegistryImpl(exam)
      val req = grpc.RegisterExamRequest(
        "e1",
        "q1",
        45,
        Instant.parse("2023-01-28T10:00:00Z").getEpochSecond,
        Instant.parse("2023-01-30T10:00:00Z").getEpochSecond,
        Seq(grpc.Person("p1", "p1 name"), grpc.Person("p2", "p2 name"))
      )
      Await.result(registry.registerExam(req), 1.second) shouldBe grpc.RegisterExamResponse.of()
      callId shouldBe "e1"
      val msg = ExamEntity.Register(
        "q1",
        ExamPeriod(Instant.parse("2023-01-28T10:00:00Z"), Instant.parse("2023-01-30T10:00:00Z")),
        45,
        Set(Person("p1", "p1 name"), Person("p2", "p2 name"))
      )
      probe.expectMessage(msg)
    }

    "unregister exam" in {
      val probe = testKit.createTestProbe[ExamEntity.Command]()
      var callId = ""
      val exam = (id: String) => 
        callId = id
        probe.ref
      val registry = RegistryImpl(exam)
      Await.result(registry.unregisterExam(grpc.UnregisterExamRequest("xyz")), 1.second) shouldBe
        grpc.UnregisterExamResponse.of()
      callId shouldBe "xyz"
      probe.expectMessage(ExamEntity.Unregister)
    }

  }
