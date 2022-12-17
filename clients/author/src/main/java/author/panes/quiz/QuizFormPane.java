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
import author.util.Factories;

import io.reactivex.rxjava3.core.Observable;
import java.util.function.Consumer;

public class QuizFormPane implements Initializable {

  @FXML private Label status;
  @FXML private Label curatorName;
  @FXML private ListView<OutPerson> authors;
  @FXML private ComboBox<OutPerson> selectedAuthor;
  @FXML private Button addAuthor;
  @FXML private ListView<OutPerson> inspectors;
  @FXML private ComboBox<OutPerson> selectedInspector;
  @FXML private Button addInspector;
  @FXML private Button setObsolete;

  @Override
  public void initialize(URL location, ResourceBundle resource) {
  }

}
