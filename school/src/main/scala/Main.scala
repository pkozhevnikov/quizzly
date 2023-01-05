package quizzly.school

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.*
import akka.http.scaladsl.Http
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.jdbc.testkit.scaladsl.SchemaUtils
import akka.projection.ProjectionBehavior
import akka.projection.ProjectionId
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

import java.time.Instant

@main
def run = Main(ActorSystem(Behaviors.empty, "ExamManagement"), FakeAuth)

object Main:

  type NowInstant = () => Instant

  def apply(system: ActorSystem[?], auth: Auth) =
    given ActorSystem[?] = system
    given ExecutionContext = system.executionContext
    given NowInstant = () => Instant.now()
    ScalikeJdbcSetup(system)
    SchemaUtils.createIfNotExists()
    val sharding = ClusterSharding(system)
    val examConfig = ExamConfig.fromConfig(system.settings.config.getConfig("school"))
    val getExam = sharding.entityRefFor(ExamEntity.EntityKey, _)
    val getFact = sharding.entityRefFor(QuizFact.EntityKey, _)
    sharding
      .init(Entity(ExamEntity.EntityKey)(ctx => ExamEntity(ctx.entityId, getFact, examConfig)))
    sharding.init(Entity(QuizFact.EntityKey)(ctx => QuizFact(ctx.entityId, examConfig)))
    val examEventProvider = EventSourcedProvider
      .eventsByTag[Exam.Event](system, JdbcReadJournal.Identifier, Exam.Tags.Single)
    val factEventProvider = EventSourcedProvider
      .eventsByTag[QuizFact.Event](system, JdbcReadJournal.Identifier, QuizFact.Tags.Single)

    JdbcProjection.createTablesIfNotExists(() => ScalikeJdbcSession())
    val examProjection = JdbcProjection.exactlyOnce(
      ProjectionId("ExamProjection", Exam.Tags.Single),
      examEventProvider,
      () => ScalikeJdbcSession(),
      () => ExamProjectionHandler()
    )
    val factProjection = JdbcProjection.exactlyOnce(
      ProjectionId("FactProjection", QuizFact.Tags.Single),
      factEventProvider,
      () => ScalikeJdbcSession(),
      () => FactProjectionHandler()
    )
    val shardedProcess = ShardedDaemonProcess(system)
    shardedProcess.init("exam-mgmt", 1, _ => ProjectionBehavior(examProjection), ProjectionBehavior.Stop)
    shardedProcess.init("facts", 1, _ => ProjectionBehavior(factProjection), ProjectionBehavior.Stop)
    val entityAware: EntityAware =
      new:
        def exam(id: String) = getExam(id)
    val host = system.settings.config.getString("frontend.http.host")
    val port = system.settings.config.getInt("frontend.http.port")
    Http()
      .newServerAt("0.0.0.0", port)
      .bind(HttpFrontend(ScalikeRead(system.name), entityAware, auth, host, port))
      .map(_.addToCoordinatedShutdown(3.seconds))
      .onComplete {
        case Success(binding) =>
          system
            .log
            .debug(
              "Exam management is online at {}:{}",
              binding.localAddress.getHostString,
              binding.localAddress.getPort
            )
        case Failure(ex) =>
          system.log.error("Failed to run exam management", ex)
          system.terminate()
      }
