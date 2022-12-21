package author.events;

import lombok.Value;

import author.dtos.OutPerson;

public interface LoginEvent {
  @Value
  class Failure implements LoginEvent {
    String username;
  }
  @Value
  class Success implements LoginEvent {
    String username;
    OutPerson user;
  }

  static LoginEvent ACCESS_DENIED = new LoginEvent() {};
}
