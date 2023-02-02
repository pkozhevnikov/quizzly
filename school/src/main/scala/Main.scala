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

type NowInstant = () => Instant

@main
def run(fakeQuizCount: Int = 0) =
  given NowInstant = () => Instant.now()
  val system = ActorSystem(Behaviors.empty, "ExamManagement")
  val fact = Main(system, FakeAuth)
  val grpcPort = system.settings.config.getInt("registry.grpc.port")
  if fakeQuizCount > 0 then
    val grpcPort = system.settings.config.getInt("registry.grpc.port")
    val settings = akka
      .grpc
      .GrpcClientSettings
      .connectToServiceAt("localhost", grpcPort)(using system)
      .withTls(false)
    val client = grpc.SchoolRegistryClient(settings)(using system)
    (1 to fakeQuizCount).foreach { n =>
      client.registerQuiz(grpc.RegisterQuizRequest(s"Q-$n", s"Q-$n title", 45))
    }

object Main:

  def apply(system: ActorSystem[?], auth: Auth)(using
      NowInstant
  ): String => EntityRef[QuizFact.Command] =
    given ActorSystem[?] = system
    given ExecutionContext = system.executionContext
    ScalikeJdbcSetup(system)
    SchemaUtils.createIfNotExists()
    val sharding = ClusterSharding(system)
    val examConfig = ExamConfig.fromConfig(system.settings.config.getConfig("school"))
    val getExam = sharding.entityRefFor(ExamEntity.EntityKey, _)
    val getFact = sharding.entityRefFor(QuizFact.EntityKey, _)
    sharding.init(Entity(ExamTracker.EntityKey)(ctx => ExamTracker(examConfig, getExam)))
    sharding.init(
      Entity(ExamEntity.EntityKey) { ctx =>
        ExamEntity(
          ctx.entityId,
          getFact,
          () => sharding.entityRefFor(ExamTracker.EntityKey, ExamTracker.ID),
          examConfig
        )
      }
    )
    sharding.init(Entity(QuizFact.EntityKey)(ctx => QuizFact(ctx.entityId, examConfig)))
    val examEventProvider = EventSourcedProvider
      .eventsByTag[Exam.Event](system, JdbcReadJournal.Identifier, Exam.Tags.Single)
    val factEventProvider = EventSourcedProvider
      .eventsByTag[QuizFact.Event](system, JdbcReadJournal.Identifier, QuizFact.Tags.Single)

    JdbcProjection.createTablesIfNotExists(() => ScalikeJdbcSession())
    val trialRegistryClient: quizzly.trial.grpc.Registry = quizzly
      .trial
      .grpc
      .RegistryClient(
        akka
          .grpc
          .GrpcClientSettings
          .connectToServiceAt(
            system.settings.config.getString("trial.registry.grpc.host"),
            system.settings.config.getInt("trial.registry.grpc.port")
          )
          .withTls(false)
      )
    val examProjection = JdbcProjection.exactlyOnce(
      ProjectionId("ExamProjection", Exam.Tags.Single),
      examEventProvider,
      () => ScalikeJdbcSession(),
      () => ExamProjectionHandler(trialRegistryClient)
    )
    val factProjection = JdbcProjection.exactlyOnce(
      ProjectionId("FactProjection", QuizFact.Tags.Single),
      factEventProvider,
      () => ScalikeJdbcSession(),
      () => FactProjectionHandler()
    )
    val shardedProcess = ShardedDaemonProcess(system)
    shardedProcess
      .init("facts", 1, _ => ProjectionBehavior(factProjection), ProjectionBehavior.Stop)
    shardedProcess
      .init("exams", 1, _ => ProjectionBehavior(examProjection), ProjectionBehavior.Stop)
    val entityAware: EntityAware =
      new:
        def exam(id: String) = getExam(id)
        def fact(id: String) = getFact(id)
    val host = system.settings.config.getString("frontend.http.host")
    val port = system.settings.config.getInt("frontend.http.port")
    val grpcPort = system.settings.config.getInt("registry.grpc.port")
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
    val grpcService = grpc.SchoolRegistryHandler(SchoolRegistryImpl(getFact, getExam))
    Http()
      .newServerAt("0.0.0.0", grpcPort)
      .bind(grpcService)
      .map(_.addToCoordinatedShutdown(3.seconds))
      .onComplete {
        case Success(binding) =>
          system
            .log
            .debug(
              "School registry is online at {}:{}",
              binding.localAddress.getHostString,
              binding.localAddress.getPort
            )
        case Failure(ex) =>
          system.log.error("Failed to run registry service", ex)
          system.terminate()
      }

    getFact
