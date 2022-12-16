package author.requests;

import author.dtos.*;

public interface ApiRequest {

  ApiRequest GET_LIST = new ApiRequest() {};
  ApiRequest GET_STAFF = new ApiRequest() {};

  class Create implements ApiRequest {
    public final InCreateQuiz content;
    public Create(InCreateQuiz content) {
      this.content = content;
    }
  }

  class AddAuthor implements ApiRequest {
    public final String quizId;
    public final String personId;
    public AddAuthor(String quizId, String personId) {
      this.quizId = quizId;
      this.personId = personId;
    }
  }

  class RemoveAuthor implements ApiRequest {
    public final String quizId;
    public final String personId;
    public RemoveAuthor(String quizId, String personId) {
      this.quizId = quizId;
      this.personId = personId;
    }
  }

  class AddInspector implements ApiRequest {
    public final String quizId;
    public final String personId;
    public AddInspector(String quizId, String personId) {
      this.quizId = quizId;
      this.personId = personId;
    }
  }

  class RemoveInspector implements ApiRequest {
    public final String quizId;
    public final String personId;
    public RemoveInspector(String quizId, String personId) {
      this.quizId = quizId;
      this.personId = personId;
    }
  }

  class SetObsolete implements ApiRequest {
    public final String quizId;
    public SetObsolete(String quizId) {
      this.quizId = quizId;
    }
  }

}

