package quizzly.school

import scala.concurrent.{ExecutionContext, Future}
import akka.actor.typed.RecipientRef

class SchoolRegistryImpl(facts: QuizID => RecipientRef[QuizFact.Command])(using ExecutionContext)
    extends grpc.SchoolRegistry:

  override def registerQuiz(in: grpc.RegisterQuizRequest): Future[grpc.RegisterQuizResponse] =
    Future {
      facts(in.id) ! QuizFact.Init(in.title, false, in.recommendedTrialLength)
      grpc.RegisterQuizResponse.of()
    }

  override def registerTrialResults(
      results: grpc.RegisterTrialResultsRequest
  ): Future[grpc.RegisterTrialResultsResponse] = ???
