package quizzly.school

import scala.concurrent.{ExecutionContext, Future}
import akka.actor.typed.RecipientRef

import java.time.Instant

class SchoolRegistryImpl(
    facts: QuizID => RecipientRef[QuizFact.Command],
    exams: ExamID => RecipientRef[Exam.Command]
)(using ExecutionContext)
    extends grpc.SchoolRegistry:

  override def registerQuiz(in: grpc.RegisterQuizRequest): Future[grpc.RegisterQuizResponse] =
    Future {
      facts(in.id) ! QuizFact.Init(in.title, false, in.recommendedTrialLength)
      grpc.RegisterQuizResponse.of()
    }

  override def registerTrialResults(
      in: grpc.RegisterTrialResultsRequest
  ): Future[grpc.RegisterTrialResultsResponse] = Future {
    exams(in.examId) !
      Exam.RegisterTrial(
        TrialOutcome(
          in.testeeId,
          in.trialId,
          Instant.ofEpochSecond(in.start),
          Instant.ofEpochSecond(in.end),
          in.solutions.map(s => Solution(s.sectionSc, s.itemSc, s.answers.toList)).toList
        )
      )
    grpc.RegisterTrialResultsResponse.of()
  }
