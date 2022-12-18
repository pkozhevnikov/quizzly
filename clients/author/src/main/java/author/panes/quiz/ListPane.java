package author.panes.quiz;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.*;

import java.net.URL;
import java.util.ResourceBundle;

import author.dtos.*;
import author.events.ApiResponse;
import author.requests.ApiRequest;
import author.util.*;

public class ListPane implements FxmlController {

  
  @FXML private TableView<OutQuizListed> list;
  @FXML private TableColumn<OutQuizListed, String> id;
  @FXML private TableColumn<OutQuizListed, String> title;
  @FXML private TableColumn<OutQuizListed, String> status;

  public ListPane(Bus<ApiResponse, ApiRequest> apiBus, Bus<Quizzes.UIMessage, Quizzes.UIMessage> uiBus) {
    this.apiBus = apiBus;
    this.uiBus = uiBus;
  }

  private Bus<ApiResponse, ApiRequest> apiBus;
  private Bus<Quizzes.UIMessage, Quizzes.UIMessage> uiBus;

  @Override
  public String fxml() {
    return "/author/panes/quiz/list-pane.fxml";
  }

  @Override
  public void initialize(URL location, ResourceBundle resource) {
    
      list.getItems().clear();
      id.setCellValueFactory(Factories.cellFactory(OutQuizListed::id));
      title.setCellValueFactory(Factories.cellFactory(OutQuizListed::title));
      status.setCellValueFactory(Factories.cellFactory(OutQuizListed::state));
      apiBus.in().ofType(ApiResponse.QuizList.class)
        .subscribe(l -> list.getItems().addAll(l.list()));

      list.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
        uiBus.out().accept(new Quizzes.ShowQuiz(nv));
      });
      apiBus.in().ofType(ApiResponse.QuizAdded.class)
        .subscribe(q -> {
          list.getItems().add(q.quiz());
          list.getSelectionModel().select(q.quiz());
        });

  }

}
