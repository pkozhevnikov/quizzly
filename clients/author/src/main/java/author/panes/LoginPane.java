package author.panes;

import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.geometry.*;
import javafx.event.*;

import java.util.function.Consumer;

import author.events.LoginEvent;
import author.requests.LoginRequest;

import io.reactivex.rxjava3.core.Observable;

import static org.pdfsam.rxjavafx.observables.JavaFxObservable.*;

public class LoginPane extends VBox {

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
    message.setId("message");

    VBox elems = new VBox(10);
    elems.setAlignment(Pos.CENTER_RIGHT);
    elems.getChildren().addAll(username, password, button);
    getChildren().addAll(message, elems);

    Observable<ActionEvent> click = actionEventsOf(button);
    click.subscribe(e -> {
      requests.accept(new LoginRequest(username.getText(), password.getText()));
      message.setText(null);
      password.setText(null);
      button.setDisable(true);
    });

    events.ofType(LoginEvent.Success.class).subscribe(e -> {
      message.setText("Logged in successfully");
      message.setStyle("-fx-text-fill:green");
    });
    events.ofType(LoginEvent.Failure.class).subscribe(e -> {
      message.setText("Wrong username or password");
      message.setStyle("-fx-text-fill:red");
      button.setDisable(false);
      password.setText(null);
    });
    
  }

}
