package quizzly.author

import akka.actor.typed.*

import org.scalatest.*
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.projection.testkit.scaladsl.{ProjectionTestKit, TestSourceProvider}

import scalikejdbc.*

import akka.projection.eventsourced.EventEnvelope
import akka.projection.ProjectionId
import akka.projection.jdbc.JdbcProjection

import scala.concurrent.{Future, ExecutionContext}

import akka.stream.scaladsl.Source

object ScalikeLocalProjectionHandlerSpec:
  val config = com.typesafe.config.ConfigFactory.parseString("""
    slick {
      profile = "slick.jdbc.H2Profile$"
      db {
        url = "jdbc:h2:mem"
        user = "sa"
        password = "sa"
        driver = "org.h2.Driver"
        numThreads = 5
        maxConnections = 5
        minConnections = 1
      }
    }
  """)

    
class ScalikeLocalProjectionHandlerSpec extends wordspec.AnyWordSpec, matchers.should.Matchers:

  val testKit = ActorTestKit(ScalikeLocalProjectionHandlerSpec.config)
  given ActorSystem[?] = testKit.system
  given ExecutionContext = testKit.system.executionContext

  ScalikeJdbcSetup(testKit.system)

  val projTestKit = ProjectionTestKit(testKit.system)

  val pub = java.util.concurrent.SubmissionPublisher[EventEnvelope[Quiz.Event]]()
  val eventSource: Source[EventEnvelope[Quiz.Event], akka.NotUsed] = 
    akka.stream.scaladsl.JavaFlowSupport.Source.fromPublisher(pub)
  val eventProvider = TestSourceProvider(eventSource, _.offset)

  val projection = JdbcProjection.exactlyOnce(
    ProjectionId("testlocal", Quiz.Tags.Single),
    eventProvider,
    () => ScalikeJdbcSession()
    () => ScalikeLocalProjectionHandler()
  )

  var _seq = 0
  def seq() =
    _seq = _seq + 1
    _seq
  def pubEvent(id: QuizID, event: Quiz.Event) =
    val s = seq()
    val time = java.time.Instant.now.toEpochMilli
    pub.submit(EventEnvelope(
      akka.persistence.query.Offset.sequence(s),
      id,
      s,
      event,
      time
    ))

  def changeDb = afterWord("change db on event:")

  "LocalProjection" should
    changeDb {

      "quiz creation" in {}

      "quiz update" in {}

      "add author" in {}

      "remove author" in {}

      "add inspector" in {}

      "remove inspector" in {}

      "change state to review" in {}

      "change state to composing" in {}

      "change state to released" in {}

      "set obsolete" in {}

    }
