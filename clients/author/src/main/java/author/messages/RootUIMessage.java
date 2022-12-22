package author.messages;

import lombok.Value;

import author.dtos.*;

public interface RootUIMessage {

  static final RootUIMessage NOT_LOGGED_IN = new RootUIMessage() {
    @Override
    public String toString() { return "NOT_LOGGED_IN"; }
  };

  static final RootUIMessage ACCESS_DENIED = new RootUIMessage() {
    @Override
    public String toString() { return "ACCESS_DENIED"; }
  };

  @Value
  class ApiError implements RootUIMessage {
    OutError error;
  }

  @Value
  class ProcessingError implements RootUIMessage {
    Throwable cause;
  }

}

