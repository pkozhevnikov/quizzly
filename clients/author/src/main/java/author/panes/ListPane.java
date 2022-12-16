package author.panes;

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

import io.reactivex.rxjava3.core.Observable;
import java.util.function.Consumer;

public class ListPane implements Initializable {

  
  @FXML private TableView<OutQuizListed> list;
  @FXML private TableColumn<OutQuizListed, String> listId;
  @FXML private TableColumn<OutQuizListed, String> listTitle;
  @FXML private TableColumn<OutQuizListed, String> listStatus;

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

  @FXML private HBox createBox;
  @FXML private Button createButton;
  
  @FXML private Label status;
  @FXML private Label curatorName;
  @FXML private ListView<OutPerson> authors;
  @FXML private ComboBox<OutPerson> selectedAuthor;
  @FXML private Button addAuthor;
  @FXML private ListView<OutPerson> inspectors;
  @FXML private ComboBox<OutPerson> selectedInspector;
  @FXML private Button addInspector;
  @FXML private Button setObsolete;

  public ListPane(Observable<ApiResponse> apiResponses, Consumer<ApiRequest> apiRequests) {
    this.apiResponses = apiResponses;
    this.apiRequests = apiRequests;
  }

  private Observable<ApiResponse> apiResponses;
  private Consumer<ApiRequest> apiRequests;

  @Override
  public void initialize(URL location, ResourceBundle resource) {

  }

  public static Node create(Observable<ApiResponse> apiResponses, Consumer<ApiRequest> apiRequests) 
        throws Exception {
    FXMLLoader loader = new FXMLLoader(ListPane.class.getResource("/author/list-pane.fxml"));
    loader.setController(new ListPane(apiResponses, apiRequests));
    return loader.load();
  }
    
}
