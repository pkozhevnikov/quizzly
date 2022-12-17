package author.requests;

import java.util.Objects;

@lombok.Value
public class LoginRequest {
  String username;
  String password;
}
