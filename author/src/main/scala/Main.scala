package quizzly.author

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
import akka.grpc.GrpcClientSettings

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

@main
def run = Main(ActorSystem(Behaviors.empty, "QuizAuthoring"), FakeAuth)

object Main:

  def apply(system: ActorSystem[?], auth: Auth) =
    given ActorSystem[?] = system
    given ExecutionContext = system.executionContext
    ScalikeJdbcSetup(system)
    SchemaUtils.createIfNotExists()
    val sharding = ClusterSharding(system)
    val quizConfig = QuizConfig.fromConfig(system.settings.config.getConfig("author"))
    val getSection = sharding.entityRefFor(SectionEditEntity.EntityKey, _)
    val getQuiz = sharding.entityRefFor(QuizEntity.EntityKey, _)
    val grpcConfig = system.settings.config.getConfig("registry")
    val schoolRegistry = quizzly
      .school
      .grpc
      .SchoolRegistryClient(
        GrpcClientSettings
          .connectToServiceAt(grpcConfig.getString("school.host"), grpcConfig.getInt("school.port"))
          .withTls(false)
      )
    val trialRegistry = quizzly
      .trial
      .grpc
      .RegistryClient(
        GrpcClientSettings
          .connectToServiceAt(grpcConfig.getString("trial.host"), grpcConfig.getInt("trial.port"))
          .withTls(false)
      )
    sharding.init(
      Entity(QuizEntity.EntityKey)(ctx =>
        QuizEntity(ctx.entityId, getSection, schoolRegistry, trialRegistry, quizConfig)
      )
    )
    sharding.init(
      Entity(SectionEditEntity.EntityKey)(ctx =>
        SectionEditEntity(ctx.entityId, getQuiz, quizConfig)
      )
    )
    val eventProvider = EventSourcedProvider
      .eventsByTag[Quiz.Event](system, JdbcReadJournal.Identifier, Quiz.Tags.Single)

    JdbcProjection.createTablesIfNotExists(() => ScalikeJdbcSession())
    val projection = JdbcProjection.exactlyOnce(
      ProjectionId("QuizViewProjection", Quiz.Tags.Single),
      eventProvider,
      () => ScalikeJdbcSession(),
      () => LocalProjectionHandler()
    )
    ShardedDaemonProcess(system)
      .init("quiz-author", 1, _ => ProjectionBehavior(projection), ProjectionBehavior.Stop)
    val entityAware: EntityAware =
      new:
        def quiz(id: String) = getQuiz(id)
        def section(id: String) = getSection(id)
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
              "Quiz authoring is online at {}:{}",
              binding.localAddress.getHostString,
              binding.localAddress.getPort
            )
        case Failure(ex) =>
          system.log.error("Failed to run quiz autoring", ex)
          system.terminate()
      }
