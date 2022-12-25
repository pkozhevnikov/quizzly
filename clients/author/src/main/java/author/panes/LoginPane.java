package author.panes;

import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.geometry.*;
import javafx.event.*;

import author.events.LoginEvent;
import author.requests.LoginRequest;
import author.util.Bus;

import static org.pdfsam.rxjavafx.observables.JavaFxObservable.*;

import lombok.val;

public class LoginPane extends VBox {

  public LoginPane(Bus<LoginEvent, LoginRequest> loginBus) {
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

    button.setOnAction(e -> {
      val uname = username.getText();
      val passw = password.getText();
      message.setText("");
      password.setText("");
      button.setDisable(true);
      loginBus.out().accept(new LoginRequest.Login(uname, passw));
    });

    loginBus.in().ofType(LoginEvent.Success.class).subscribe(e -> {
      message.setText("Logged in successfully");
      message.setStyle("-fx-text-fill:darkgreen");
    });
    loginBus.in().ofType(LoginEvent.Failure.class).subscribe(e -> {
      message.setText("Wrong username or password");
      message.setStyle("-fx-text-fill:darkred");
      button.setDisable(false);
      password.setText("");
    });

    loginBus.in().ofType(LoginEvent.LoggedOut.class).subscribe(e -> {
      message.setText("");
      button.setDisable(false);
      username.setText("");
      password.setText("");
    });
    
  }

}
