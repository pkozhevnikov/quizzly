package author.messages;

import lombok.Value;

import author.dtos.*;

public interface RootUIMessage {

  interface Info extends RootUIMessage {}
  interface Warn extends RootUIMessage {}
  interface Error extends RootUIMessage {}

  static final RootUIMessage NOT_LOGGED_IN = new Error() {
    @Override
    public String toString() { return "NOT_LOGGED_IN"; }
  };

  static final RootUIMessage ACCESS_DENIED = new Error() {
    @Override
    public String toString() { return "ACCESS_DENIED"; }
  };

  static final RootUIMessage CLEAR = new Info() {
    @Override public String toString() { return "CLEAR"; }
  };

  @Value
  class ApiError implements Error {
    OutError error;
  }

  @Value
  class ProcessingError implements Error {
    Throwable cause;
  }

}

