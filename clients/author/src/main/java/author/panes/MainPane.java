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
import java.util.concurrent.CompletionException;

import author.dtos.*;
import author.events.*;
import author.requests.*;
import author.util.*;
import author.messages.*;

import author.panes.LoginPane;
import author.panes.quiz.Quizzes;
import author.panes.quiz.QuizPane;
import author.panes.section.SectionPane;

import io.reactivex.rxjava3.core.Observable;

import lombok.val;

public class MainPane extends BorderPane {

  private Node quizzes;
  private Node quizPane;
  private Node sectionPane;

  private Button home;
  private Button logout;
  private Label userName;
  private Label message;

  private Bus<MainUIMessage, MainUIMessage> uiBus;

  public MainPane(
    Bus<ApiResponse, ApiRequest> apiBus, 
    Bus<LoginEvent, LoginRequest> loginBus,
    Bus<RootUIMessage, RootUIMessage> rootUiBus
  ) {
    this.uiBus = new PipeBus<>();

    getStylesheets().add("/author/common.css");
    
    Node topBar = initTop();
    quizzes = Quizzes.create(apiBus);
    quizzes.setId("quizzes");
    quizPane = Factories.nodeWith(new QuizPane(apiBus, uiBus));
    quizPane.setId("quizPane");
    sectionPane = new SectionPane(apiBus, uiBus);

    setTop(topBar);

    loginBus.in().ofType(LoginEvent.Success.class).subscribe(e -> {
      userName.setText(e.user().name());
      apiBus.out().accept(ApiRequest.GET_STAFF);
      apiBus.out().accept(ApiRequest.GET_LIST);
      uiBus.out().accept(new MainUIMessage.ActingAs(e.user()));
      setCenter(quizzes);
    });

    apiBus.in().ofType(ApiResponse.FullQuiz.class).subscribe(e -> {
      uiBus.out().accept(new MainUIMessage.SetQuiz(e.quiz()));
      setCenter(quizPane);
    });

    logout.setOnAction(e -> {
      if (getCenter() == sectionPane) {
        apiBus.in().ofType(ApiResponse.SectionDischarged.class).take(1).subscribe(e1 ->
          loginBus.out().accept(LoginRequest.LOGOUT)
        );
        apiBus.out().accept(new ApiRequest.DischargeSection(currentSC));
      } else
        loginBus.out().accept(LoginRequest.LOGOUT);
    });
    home.setOnAction(e -> {
      if (getCenter() == sectionPane) {
        apiBus.out().accept(new ApiRequest.DischargeSection(currentSC));
      } else {
        setCenter(quizzes);
      }
    });

    uiBus.in().ofType(MainUIMessage.EditSection.class).subscribe(e -> {
      apiBus.out().accept(new ApiRequest.OwnSection(e.quizId(), e.section().sc()));
    });
    apiBus.in().ofType(ApiResponse.SectionOwned.class).subscribe(e -> {
      currentSC = e.sc();
      setCenter(sectionPane);
    });
    apiBus.in().ofType(ApiResponse.SectionDischarged.class).subscribe(e -> setCenter(quizPane));

    rootUiBus.in().subscribe(e -> {
      String color = null;
      if (e instanceof RootUIMessage.Info) color = "darkgreen";
      else if (e instanceof RootUIMessage.Warn) color = "gold";
      else color = "darkred";
      message.setStyle("-fx-text-fill:" + color);
    });
    rootUiBus.in().subscribe(e -> {
      if (e == RootUIMessage.NOT_LOGGED_IN)
        message.setText("Not logged in");
      else if (e == RootUIMessage.ACCESS_DENIED)
        message.setText("Access denied");
      else if (e instanceof RootUIMessage.ApiError) {
        val ae = (RootUIMessage.ApiError) e;
        message.setText(String.format("%s: %s %s",
          ae.error().reason().code(),
          ae.error().reason().phrase(),
          ae.error().clues()
        ));
      } else if (e instanceof RootUIMessage.ProcessingError) {
        val pe = (RootUIMessage.ProcessingError) e;
        if (pe.cause() instanceof CompletionException) {
          message.setText(pe.cause().getCause().getMessage());
        } else {
          message.setText(pe.cause().getMessage());
        }
      } else if (e == RootUIMessage.CLEAR)
        message.setText("");
      else if (e instanceof RootUIMessage.TextMessage) 
        message.setText(((RootUIMessage.TextMessage) e).text());
    });

  }

  private String currentSC;

  private Node initTop() {
    HBox topBar = new HBox();
    topBar.setSpacing(10);
    topBar.setAlignment(Pos.CENTER_LEFT);
    home = new Button();
    home.setId("home");
    home.getStyleClass().add("home");
    home.setTooltip(new Tooltip("To quiz list"));
    message = new Label();
    message.setId("message");
    HBox topRight = new HBox();
    topRight.setAlignment(Pos.CENTER_RIGHT);
    topRight.setSpacing(10);
    userName = new Label();
    userName.setId("username");
    userName.setStyle("-fx-font-weight:bold");
    logout = new Button();
    logout.setId("logout");
    logout.getStyleClass().add("logout");
    logout.setTooltip(new Tooltip("Logout"));
    topRight.getChildren().addAll(userName, logout);
    topBar.getChildren().addAll(home, message, topRight);
    HBox.setHgrow(topRight, Priority.ALWAYS);
    return topBar;
  }

}
