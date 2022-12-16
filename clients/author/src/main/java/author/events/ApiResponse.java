package author.events;

import java.util.List;
import author.dtos.*;

public interface ApiResponse {

  class QuizList implements ApiResponse {
    public final List<OutQuizListed> list;
    public QuizList(List<OutQuizListed> list) {
      this.list = list;
    }
  }

  class PersonList implements ApiResponse {
    public final List<OutPerson> list;
    public PersonList(List<OutPerson> list) {
      this.list = list;
    }
  }

  class QuizCreated implements ApiResponse {
    public final OutQuizListed quiz;
    public QuizCreated(OutQuizListed quiz) {
      this.quiz = quiz;
    }
  }
  
  class AuthorAdded implements ApiResponse {
    public final String quizId;
    public final String personId;
    public AuthorAdded(String quizId, String personId) {
      this.quizId = quizId;
      this.personId = personId;
    }
  }

  class AuthorRemoved implements ApiResponse {
    public final String quizId;
    public final String personId;
    public AuthorRemoved(String quizId, String personId) {
      this.quizId = quizId;
      this.personId = personId;
    }
  }

  class InspectorAdded implements ApiResponse {
    public final String quizId;
    public final String personId;
    public InspectorAdded(String quizId, String personId) {
      this.quizId = quizId;
      this.personId = personId;
    }
  }

  class InspectorRemoved implements ApiResponse {
    public final String quizId;
    public final String personId;
    public InspectorRemoved(String quizId, String personId) {
      this.quizId = quizId;
      this.personId = personId;
    }
  }

  class GotObsolete implements ApiResponse {
    public final String quizId;
    public GotObsolete(String quizId) {
      this.quizId = quizId;
    }
  }

}

