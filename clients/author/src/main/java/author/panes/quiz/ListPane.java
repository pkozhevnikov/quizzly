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

public class ListPane implements Initializable {

  
  @FXML private TableView<OutQuizListed> list;
  @FXML private TableColumn<OutQuizListed, String> listId;
  @FXML private TableColumn<OutQuizListed, String> listTitle;
  @FXML private TableColumn<OutQuizListed, String> listStatus;

  @FXML private HBox createBox;
  @FXML private Button createButton;
  
  public ListPane(Bus<ApiResponse, ApiRequest> apiBus) {
    this.apiBus = apiBus;
  }

  private Bus<ApiResponse, ApiRequest> apiBus;

  public interface UIMessage {}
  public static final UIMessage CLEAR_CREATE_PANE = new UIMessage() {};
  public static final UIMessage HIDE_CREATE_PANE = new UIMessage() {};

  @Override
  public void initialize(URL location, ResourceBundle resource) {
    
      list.getItems().clear();
      listId.setCellValueFactory(Factories.cellFactory(OutQuizListed::id));
      listTitle.setCellValueFactory(Factories.cellFactory(OutQuizListed::title));
      listStatus.setCellValueFactory(Factories.cellFactory(OutQuizListed::state));
      apiBus.in().ofType(ApiResponse.QuizList.class)
        .subscribe(l -> list.getItems().addAll(l.list()));

  }

  public static Node create(Bus<ApiResponse, ApiRequest> apiBus) 
        throws Exception {
    FXMLLoader loader = new FXMLLoader(ListPane.class.getResource("/author/panes/quiz/list-pane.fxml"));
    loader.setController(new ListPane(apiBus));
    return loader.load();
  }
    
}
