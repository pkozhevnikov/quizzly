package author.panes;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.*;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.*;
import java.util.stream.*;
import java.util.function.*;

import author.dtos.*;
import author.events.*;
import author.requests.*;
import author.util.*;
import author.messages.*;

import author.panes.LoginPane;
import author.panes.MainPane;

import io.reactivex.rxjava3.core.Observable;
import static org.pdfsam.rxjavafx.observables.JavaFxObservable.*;

import lombok.val;

public class RootPane extends BorderPane {

  public RootPane(Bus<ApiResponse, ApiRequest> apiBus, Bus<LoginEvent, LoginRequest> loginBus) {

    val loginPane = new LoginPane(loginBus);
    loginPane.setId("loginPane");
    loginPane.setMaxSize(300, 250);
    val mainPane = new MainPane(apiBus, loginBus);
    mainPane.setId("mainPane");

    loginBus.in().ofType(LoginEvent.Success.class).subscribe(e -> setCenter(mainPane));
    loginBus.in().ofType(LoginEvent.LoggedOut.class).subscribe(e -> setCenter(loginPane));

    setCenter(loginPane);
  }


}
