package author.requests;

import java.util.Objects;

public class LoginRequest {
  public final String username;
  public final String password;
  public LoginRequest(String username, String password) {
    this.username = username;
    this.password = password;
  }
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof LoginRequest)) return false;
    LoginRequest other = (LoginRequest) o;
    return other.username.equals(username) && other.password.equals(password);
  }
  @Override
  public int hashCode() {
    return Objects.hash(username, password);
  }

  @Override
  public String toString() {
    return String.format("LoginRequest(username=%s,password=%s)", username, password);
  }

}
