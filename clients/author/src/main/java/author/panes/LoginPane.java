package author.panes;

import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.geometry.*;

import java.util.function.Consumer;

import author.Auth;

import static io.reactivex.rxjavafx.observables.JavaFxObservable.*;

public class LoginPane extends VBox {

  private Auth auth = Auth.NULL;

  public LoginPane(Observable<LoginEvent> events, Consumer<LoginRequest> requests) {
    super();
    setPadding(new Insets(20, 20, 20, 20));
    setSpacing(10);
    setMinWidth(250);

    TextField username = new TextField();
    PasswordField password = new PasswordField();
    Button button = new Button("Login");
    Label message = new Label();
    username.setId("username");
    username.setPromptText("Username");
    password.setId("password");
    password.setPromptText("Password");
    button.setId("button");
    message.setStyle("-fx-text-fill:FIREBRICK");
    message.setId("message");

    button.setOnAction(e -> {
      message.setText(null);
      button.setDisable(true);
      if (!auth.login(username.getText(), password.getText())) {
        message.setText("Wrong username or password");
        button.setDisable(false);
        password.setText(null);
      }
    });
    VBox elems = new VBox(10);
    elems.setAlignment(Pos.CENTER_RIGHT);
    elems.getChildren().addAll(username, password, button);

    getChildren().addAll(message, elems);

    Observable<ActionEvent> click = actionEventsOf(button);
    click.map(e -> null).subscribe(message::setText);
    click.map(e -> true).subscribe(button::setDisable);
    click.map(e -> new LoginRequest(username.getText(), password.getText()))
      .subscribe(requests::accept);

    events.onType(LoginEvent.Success.class).map(
  }

  public void setAuth(Auth auth) {
    this.auth = auth;
  }

}
