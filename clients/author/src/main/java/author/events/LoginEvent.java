package author.events;

public interface LoginEvent {

  class Failure implements LoginEvent {
    public final String username;
    public Failure(String username) {
      this.username = username;
    }
    public String username() {
      return username;
    }
  }

  class Success implements LoginEvent {
    public final String username;
    public final String name;
    public Success(String username, String name) {
      this.username = username;
      this.name = name;
    }
    public String username() {
      return username;
    }
    public String name() {
      return name;
    }
  }

}
