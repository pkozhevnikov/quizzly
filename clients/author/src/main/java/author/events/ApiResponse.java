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
  class FullQuiz implements ApiResponse {
    OutFullQuiz quiz;
  }
  @Value
  class QuizAdded implements ApiResponse {
    OutQuizListed quiz;
  }
  @Value
  class PersonList implements ApiResponse {
    List<OutPerson> list;
  }
  interface WithQuizId extends ApiResponse {
    String quizId();
  }
  interface WithPersonId extends ApiResponse {
    String personId();
  }
  @Value
  class AuthorAdded implements WithQuizId, WithPersonId {
    String quizId;
    String personId;
  }
  @Value
  class AuthorRemoved implements WithQuizId, WithPersonId {
    String quizId;
    String personId;
  }
  @Value
  class InspectorAdded implements WithQuizId, WithPersonId {
    String quizId;
    String personId;
  }
  @Value
  class InspectorRemoved implements WithQuizId, WithPersonId {
    String quizId;
    String personId;
  }
  @Value
  class GotObsolete implements WithQuizId {
    String quizId;
  } 
  @Value
  class ReadySet implements WithQuizId, WithPersonId {
    String quizId;
    String personId;
  }
  @Value
  class ReadyUnset implements WithQuizId, WithPersonId {
    String quizId;
    String personId;
  }
  @Value
  class Approved implements WithQuizId, WithPersonId {
    String quizId;
    String personId;
  }
  @Value
  class Disapproved implements WithQuizId, WithPersonId {
    String quizId;
    String personId;
  }

  @Value
  class SectionCreated implements WithQuizId {
    String quizId;
    OutSection section;
  }
  @Value
  class SectionMoved implements WithQuizId {
    String quizId;
    List<String> scs;
  }
  @Value
  class SectionRemoved implements WithQuizId {
    String quizId;
    String sc;
  }
  @Value
  class ItemAdded implements ApiResponse {
    String sectionSC;
    String sc;
  }
  @Value
  class ItemRemoved implements ApiResponse {
    String sectionSC;
    String sc;
  }
  @Value
  class ItemMoved implements ApiResponse {
    String sectionSC;
    List<String> scs;
  }
  @Value
  class SectionOwned implements WithQuizId {
    String quizId;
    OutSection section;
  }
  @Value
  class SectionDischarged implements ApiResponse {
    String sc;
  }
}

