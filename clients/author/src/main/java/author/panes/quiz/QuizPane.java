package author.panes.quiz;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.net.URL;
import java.util.ResourceBundle;

import author.dtos.*;
import author.events.ApiResponse;
import author.requests.ApiRequest;
import author.util.*;

import io.reactivex.rxjava3.core.Observable;
import java.util.function.Consumer;

import lombok.val;

public class QuizPane implements FxmlController {

  @FXML private Label id;
  @FXML private Label status;
  @FXML private TextField title;
  @FXML private TextArea intro;
  @FXML private TextField recommendedLength;
  @FXML private Label curatorName;
  @FXML private VBox readiness;
  @FXML private VBox approvals;
  @FXML private Button save;
  @FXML private Button setReady;
  @FXML private Button unsetReady;
  @FXML private Button approve;
  @FXML private Button disapprove;
  @FXML private ListView sections;

  @Override
  public String fxml() {
    return "/authors/panes/quiz/quiz-pane.fxml";
  }

  @Override
  public void initialize(URL location, ResourceBundle resources) {

  }

}
