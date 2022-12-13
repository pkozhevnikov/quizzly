package quizzly.author

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.*
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.jdbc.testkit.scaladsl.SchemaUtils
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.ProjectionId
import akka.projection.ProjectionBehavior

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

import scala.util.{Success, Failure}

import akka.http.scaladsl.Http
import scalikejdbc.*

@main
def run =
  val sys = ActorSystem(Behaviors.empty, "QuizAuthoring")
  Main(sys, FakeAuth)

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
    sharding
      .init(Entity(QuizEntity.EntityKey)(ctx => QuizEntity(ctx.entityId, getSection, quizConfig)))
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
    Http()
      .newServerAt("0.0.0.0", system.settings.config.getInt("frontend-http-port"))
      .bind(HttpFrontend(ScalikeRead(system.name), entityAware, auth))
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
