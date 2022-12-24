package author.requests;

import author.dtos.*;
import java.util.Set;

import lombok.*;

public interface ApiRequest {

  ApiRequest GET_LIST = new ApiRequest() {
    @Override public String toString() { return "GET_LIST"; }
  };
  ApiRequest GET_STAFF = new ApiRequest() {
    @Override public String toString() { return "GET_STAFF"; }
  };

  @Value
  class GetQuiz implements ApiRequest {
    String id;
  }

  @Value
  class Create implements ApiRequest {
    String id;
    String title;
    Set<String> authors;
    Set<String> inspectors;
  }
  @Value
  class AddAuthor implements ApiRequest {
    String quizId;
    String personId;
  }
  @Value
  class RemoveAuthor implements ApiRequest {
    String quizId;
    String personId;
  }
  @Value
  class AddInspector implements ApiRequest {
    String quizId;
    String personId;
  }
  @Value
  class RemoveInspector implements ApiRequest {
    String quizId;
    String personId;
  }
  @Value
  class SetObsolete implements ApiRequest {
    String quizId;
  }

  @Value
  class MoveSection implements ApiRequest {
    String quizId;
    String sc;
    boolean up;
  }
  @Value
  class RemoveSection implements ApiRequest {
    String sc;
    String quizId;
  }

  @Value
  class UpdateQuiz implements ApiRequest {
    String quizId;
    String title;
    String intro;
    int recommendedLength;
  }
  @Value
  class SetReady implements ApiRequest {
    String quizId;
  }
  @Value
  class UnsetReady implements ApiRequest {
    String quizId;
  }
  @Value
  class Approve implements ApiRequest {
    String quizId;
  }
  @Value
  class Disapprove implements ApiRequest {
    String quizId;
  }

  @Value
  class CreateSection implements ApiRequest {
    String quizId;
    String title;
  }

  @Value
  class SaveItem implements ApiRequest {
    String sectionSC;
    OutItem item;
  }
  @Value
  class RemoveItem implements ApiRequest {
    String sectionSC;
    String itemSC;
  }
  @Value
  class MoveItem implements ApiRequest {
    String sectionSC;
    String itemSC;
    boolean up;
  }
}

