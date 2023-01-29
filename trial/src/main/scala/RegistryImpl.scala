package quizzly.trial

import scala.concurrent.{Future, ExecutionContext}
import akka.actor.typed.{ActorSystem, RecipientRef}
import akka.serialization.jackson.JacksonObjectMapperProvider

import java.time.*

import scalikejdbc.*

class RegistryImpl(exams: String => RecipientRef[ExamEntity.Command])(using sys: ActorSystem[?])
    extends grpc.Registry,
      QuizRegistry:

  given ExecutionContext = sys.executionContext
  given toList[T]: Conversion[Seq[T], List[T]] = _.toList

  override def registerQuiz(in: grpc.RegisterQuizRequest): Future[grpc.RegisterQuizResponse] =
    Future {
      val quiz = Quiz(
        in.id,
        in.title,
        in.intro,
        in.sections
          .map { sect =>
            Section(
              sect.sc,
              sect.title,
              sect.intro,
              sect
                .items
                .map { it =>
                  Item(
                    it.sc,
                    it.intro,
                    Statement(it.definition.text, it.definition.image),
                    it.hints.map(_.alts.map(s => Statement(s.text, s.image)).toList),
                    it.hintsVisible,
                    it.solutions
                  )
                }
            )
          }
      )
      val mapper = JacksonObjectMapperProvider.get(sys).getOrCreate("trial", None)
      NamedDB(sys.name).localTx { implicit session =>
        sql"insert into quiz (id,title,content) values (?,?,?)"
          .bind(in.id, in.title, mapper.writeValueAsBytes(quiz))
          .update
          .apply()
      }
      grpc.RegisterQuizResponse.of()
    }

  override def get(id: QuizID): Future[Quiz] = Future {
    val bytes = NamedDB(sys.name).readOnly { implicit session =>
      sql"select content from quiz where id=?".bind(id).map(_.bytes("content")).single.apply()
    }
    bytes match
      case Some(bs) =>
        val mapper = JacksonObjectMapperProvider.get(sys).getOrCreate("trial", None)
        mapper.readValue(bs, classOf[Quiz])
      case None =>
        throw java.util.NoSuchElementException(s"quiz [$id] not found")
  }

  override def registerExam(in: grpc.RegisterExamRequest): Future[grpc.RegisterExamResponse] =
    Future {
      val reg = ExamEntity.Register(
        in.quizId,
        ExamPeriod(Instant.ofEpochSecond(in.start), Instant.ofEpochSecond(in.end)),
        in.trialLength,
        in.testees.map(p => Person(p.id, p.name)).toSet
      )
      exams(in.id) ! reg
      grpc.RegisterExamResponse.of()
    }
