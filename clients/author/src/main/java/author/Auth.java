package author;

public interface Auth {

  boolean login(String username, String password);

  Auth NULL = new Auth() {
    public boolean login(String un, String pw) {
      return false;
    }
  };

}
