package author.events;

import lombok.Value;

public interface LoginEvent {
  @Value
  class Failure implements LoginEvent {
    String username;
  }
  @Value
  class Success implements LoginEvent {
    String username;
    String name;
  }
}
