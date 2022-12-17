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

public class CreateQuizPane implements Initializable {

  @FXML private GridPane newForm;
  @FXML private TextField newTitle;
  @FXML private TextField newId;
  @FXML private ListView<OutPerson> newAuthors;
  @FXML private ComboBox<OutPerson> newSelectedAuthor;
  @FXML private Button newAddAuthor;
  @FXML private ListView<OutPerson> newInspectors;
  @FXML private ComboBox<OutPerson> newSelectedInspector;
  @FXML private Button newAddInspector;
  @FXML private Button newSave;
  @FXML private Button newCancel;


  private CreateQuizPane(Bus<ListPane.UIMessage, ListPane.UIMessage> uiBus,
    Bus<ApiResponse, ApiRequest> apiBus) {
    this.uiBus = uiBus;
    this.apiBus = apiBus;
  }

  private final Bus<ListPane.UIMessage, ListPane.UIMessage> uiBus;
  private final Bus<ApiResponse, ApiRequest> apiBus;

  
  public static Node create(Bus<ListPane.UIMessage, ListPane.UIMessage> uiBus,
      Bus<ApiResponse, ApiRequest> apiBus) throws Exception {
    FXMLLoader loader = new FXMLLoader(CreateQuizPane.class.getResource("/author/panes/quiz/create-quiz.fxml"));
    loader.setController(new CreateQuizPane(uiBus, apiBus));
    return loader.load();
  }
    

  @Override
  public void initialize(URL location, ResourceBundle resource) {
  }
}
