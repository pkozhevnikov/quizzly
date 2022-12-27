package author.messages;

import lombok.Value;

import author.dtos.*;

public interface MainUIMessage {

  @Value
  class ActingAs implements MainUIMessage {
    OutPerson person;
  }
  @Value
  class SetQuiz implements MainUIMessage {
    OutFullQuiz quiz;
  }

  @Value
  class EditSection implements MainUIMessage {
    String quizId;
    String sc;
  }

}
