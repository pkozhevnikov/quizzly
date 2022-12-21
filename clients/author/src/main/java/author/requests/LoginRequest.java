package author.requests;

import java.util.Objects;

import lombok.Value;

public interface LoginRequest {

  @Value
  public class Login implements LoginRequest {
    String username;
    String password;
  }

  public static final LoginRequest LOGOUT = new LoginRequest() {};

}
