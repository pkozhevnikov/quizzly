package author.events;

import java.util.List;
import author.dtos.*;
import lombok.Value;

public interface ApiResponse {
  @Value
  class QuizList implements ApiResponse {
    List<OutQuizListed> list;
  }
  @Value
  class QuizAdded implements ApiResponse {
    OutQuizListed quiz;
  }
  @Value
  class PersonList implements ApiResponse {
    List<OutPerson> list;
  }
  @Value
  class QuizCreated implements ApiResponse {
    OutQuizListed quiz;
  }
  @Value
  class AuthorAdded implements ApiResponse {
    String quizId;
    String personId;
  }
  @Value
  class AuthorRemoved implements ApiResponse {
    String quizId;
    String personId;
  }
  @Value
  class InspectorAdded implements ApiResponse {
    String quizId;
    String personId;
  }
  @Value
  class InspectorRemoved implements ApiResponse {
    String quizId;
    String personId;
  }
  @Value
  class GotObsolete implements ApiResponse {
    String quizId;
  }

}

