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
  val list =
    if fakeQuizCount > 0 then
      (1 to fakeQuizCount)
        .map { n =>
          val item1 = Item(
            "i1",
            "i2 intro",
            Statement("i1 def", None),
            List(
              List(Statement("i1 h1 a1", None), Statement("i1 h1 a2", None)),
              List(Statement("i1 h2 a1", None), Statement("i1 h2 a2", None))
            ),
            true,
            List(0, 1)
          )
          val item2 = Item("i2", "i2 intro", Statement("i2 def", None), List(), false, List(1, 2))
          val item3 = Item("i3", "i3 intro", Statement("i3 def", None), List(), false, List(3, 4))
          val section1 = Section("s1", "s1 title", "s1 intro", List(item1, item2))
          val section2 = Section("s2", "s2 title", "s2 intro", List(item3))
          (s"q$n", Quiz(s"q$n", s"q$n title", s"q$n intro", List(section1, section2)))
        }
        .toMap
    else
      Map.empty[QuizID, Quiz]
  val quizreg = FakeQuizRegistry(list)
  given NowInstant = () => Instant.now()
  val exam = Main(ActorSystem(Behaviors.empty, "Trial"), FakeAuth, quizreg)
  if fakeQuizCount > 0 then
    (1 to fakeQuizCount).foreach { n =>
      val period = ExamPeriod(Instant.now(), Instant.now().plus(2, ChronoUnit.DAYS))
      val trialLength = 50
      val testees = FakeAuth.all.values.toSet.take(8)
      exam(s"E-$n") ! ExamEntity.Register(s"q$n", period, trialLength, testees)
    }

object Main:

  def apply(system: ActorSystem[?], auth: Auth, quizRegistry: QuizRegistry)(using
      NowInstant
  ): String => EntityRef[ExamEntity.Command] =
    given ActorSystem[?] = system
    given ExecutionContext = system.executionContext
    SchemaUtils.createIfNotExists()
    val sharding = ClusterSharding(system)
    val getExam = sharding.entityRefFor(ExamEntity.EntityKey, _)
    val getTrial = sharding.entityRefFor(TrialEntity.EntityKey, _)
    sharding.init(Entity(ExamEntity.EntityKey)(ctx => ExamEntity(ctx.entityId)))
    sharding
      .init(Entity(TrialEntity.EntityKey)(ctx => TrialEntity(ctx.entityId, getExam, quizRegistry)))
    val entityAware: EntityAware =
      new:
        def exam(id: String) = getExam(id)
        def trial(id: String) = getTrial(id)
    val host = system.settings.config.getString("frontend.http.host")
    val port = system.settings.config.getInt("frontend.http.port")
    Http()
      .newServerAt("0.0.0.0", port)
      .bind(HttpFrontend(quizRegistry, entityAware, auth, host, port))
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
          system.log.error("Failed to run exam management", ex)
          system.terminate()
      }

    getExam
