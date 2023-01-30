package quizzly.trial

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

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

import java.time.*
import java.time.temporal.ChronoUnit

type NowInstant = () => Instant

class FakeQuizRegistry(list: Map[QuizID, Quiz]) extends QuizRegistry:
  import ExecutionContext.Implicits.global
  def get(id: QuizID) =
    list.get(id) match
      case Some(q) =>
        Future(q)
      case _ =>
        Future.failed(java.util.NoSuchElementException(s"quiz $id not found"))

@main
def run(fakeQuizCount: Int = 0) =
  given NowInstant = () => Instant.now()
  val system = ActorSystem(Behaviors.empty, "Trial")
  val exam = Main(system, FakeAuth)
  if fakeQuizCount > 0 then
    val grpcPort = system.settings.config.getInt("registry.grpc.port")
    val settings = akka
      .grpc
      .GrpcClientSettings
      .connectToServiceAt("localhost", grpcPort)(using system)
    val client = grpc.RegistryClient(settings)(using system)
    val item1 = grpc.Item(
      "i1",
      "i2 intro",
      grpc.Statement("i1 def", None),
      Seq(
        grpc.Hint(Seq(grpc.Statement("i1 h1 a1", None), grpc.Statement("i1 h1 a2", None))),
        grpc.Hint(Seq(grpc.Statement("i1 h2 a1", None), grpc.Statement("i1 h2 a2", None)))
      ),
      true,
      Seq(0, 1)
    )
    val item2 = grpc.Item("i2", "i2 intro", grpc.Statement("i2 def", None), Seq(), false, Seq(1, 2))
    val item3 = grpc.Item("i3", "i3 intro", grpc.Statement("i3 def", None), Seq(), false, Seq(3, 4))
    val section1 = grpc.Section("s1", "s1 title", "s1 intro", Seq(item1, item2))
    val section2 = grpc.Section("s2", "s2 title", "s2 intro", Seq(item3))
    (1 to fakeQuizCount).foreach { n =>
      var quiz = grpc
        .RegisterQuizRequest(s"q$n", s"q$n title", s"q$n intro", Seq(section1, section2))
      client.registerQuiz(quiz)
      val period = ExamPeriod(Instant.now(), Instant.now().plus(2, ChronoUnit.DAYS))
      val trialLength = 50
      val testees = FakeAuth.all.values.toSet.take(8)
      exam(s"E-$n") ! ExamEntity.Register(s"q$n", period, trialLength, testees)
    }

object Main:

  def apply(system: ActorSystem[?], auth: Auth)(using
      NowInstant
  ): String => EntityRef[ExamEntity.Command] =
    given ActorSystem[?] = system
    given ExecutionContext = system.executionContext
    ScalikeJdbcSetup(system)
    SchemaUtils.createIfNotExists()
    val sharding = ClusterSharding(system)
    val getExam = sharding.entityRefFor(ExamEntity.EntityKey, _)
    val getTrial = sharding.entityRefFor(TrialEntity.EntityKey, _)
    val registry = RegistryImpl(getExam)
    sharding.init(Entity(ExamEntity.EntityKey)(ctx => ExamEntity(ctx.entityId)))
    sharding
      .init(Entity(TrialEntity.EntityKey)(ctx => TrialEntity(ctx.entityId, getExam, registry)))
    val entityAware: EntityAware =
      new:
        def exam(id: String) = getExam(id)
        def trial(id: String) = getTrial(id)

    val examEventProvider = EventSourcedProvider
      .eventsByTag[ExamEntity.Event](system, JdbcReadJournal.Identifier, ExamEntity.Tags.Single)
    JdbcProjection.createTablesIfNotExists(() => ScalikeJdbcSession())
    val examProjection = JdbcProjection.exactlyOnce(
      ProjectionId("ExamProjection", ExamEntity.Tags.Single),
      examEventProvider,
      () => ScalikeJdbcSession(),
      () => ExamProjectionHandler()
    )
    val shardedProcess = ShardedDaemonProcess(system)
    shardedProcess
      .init("exams", 1, _ => ProjectionBehavior(examProjection), ProjectionBehavior.Stop)

    val host = system.settings.config.getString("frontend.http.host")
    val port = system.settings.config.getInt("frontend.http.port")
    val grpcPort = system.settings.config.getInt("registry.grpc.port")
    Http()
      .newServerAt("0.0.0.0", port)
      .bind(HttpFrontend(ReadImpl(system.name), entityAware, auth, host, port))
      .map(_.addToCoordinatedShutdown(3.seconds))
      .onComplete {
        case Success(binding) =>
          system
            .log
            .debug(
              "Trial is online at {}:{}",
              binding.localAddress.getHostString,
              binding.localAddress.getPort
            )
        case Failure(ex) =>
          system.log.error("Failed to run trial module", ex)
          system.terminate()
      }
    val grpcService = grpc.RegistryHandler(registry)
    Http()
      .newServerAt("0.0.0.0", grpcPort)
      .bind(grpcService)
      .map(_.addToCoordinatedShutdown(3.seconds))
      .onComplete {
        case Success(binding) =>
          system
            .log
            .debug(
              "Trial registry is online at {}:{}",
              binding.localAddress.getHostString,
              binding.localAddress.getPort
            )
        case Failure(ex) =>
          system.log.error("Failed to run registry service", ex)
          system.terminate()
      }
    getExam
