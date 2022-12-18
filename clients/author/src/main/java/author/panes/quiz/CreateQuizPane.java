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
import java.util.stream.Collectors;

import lombok.val;

public class CreateQuizPane implements FxmlController {

  @FXML private TextField title;
  @FXML private TextField id;
  @FXML private ListView<OutPerson> authors;
  @FXML private ComboBox<OutPerson> selectedAuthor;
  @FXML private Button addAuthor;
  @FXML private ListView<OutPerson> inspectors;
  @FXML private ComboBox<OutPerson> selectedInspector;
  @FXML private Button addInspector;
  @FXML private Button save;
  @FXML private Button cancel;


  public CreateQuizPane(
    Bus<ApiResponse, ApiRequest> apiBus,
    Bus<Quizzes.UIMessage, Quizzes.UIMessage> uiBus
  ) {
    this.uiBus = uiBus;
    this.apiBus = apiBus;
  }

  private final Bus<Quizzes.UIMessage, Quizzes.UIMessage> uiBus;
  private final Bus<ApiResponse, ApiRequest> apiBus;

  @Override
  public String fxml() {
    return "/author/panes/quiz/create-quiz.fxml";
  }
  
  @Override
  public void initialize(URL location, ResourceBundle resource) {
    val comboCellFactory = Factories.listCellFactory(OutPerson::name);
    val listCellFactory = Factories.listCellFactory(OutPerson::name, "X", 
      (list, p) -> list.getItems().remove(p));
    authors.setCellFactory(listCellFactory);
    selectedAuthor.setButtonCell(comboCellFactory.call(null));
    selectedAuthor.setCellFactory(comboCellFactory);
    inspectors.setCellFactory(listCellFactory);
    selectedInspector.setCellFactory(comboCellFactory);
    selectedInspector.setButtonCell(comboCellFactory.call(null));

    uiBus.in().filter(v -> v == Quizzes.CLEAR_CREATE_PANE)
      .subscribe(m -> clear());
    apiBus.in().ofType(ApiResponse.PersonList.class)
      .subscribe(l -> {
        selectedAuthor.getItems().clear();
        selectedAuthor.getItems().addAll(l.list());
        selectedInspector.getItems().clear();
        selectedInspector.getItems().addAll(l.list());
      });

    addAuthor.setOnAction(e -> authors.getItems().add(selectedAuthor.getValue()));
    addInspector.setOnAction(e -> inspectors.getItems().add(selectedInspector.getValue()));

    cancel.setOnAction(e -> {
      clear();
      uiBus.out().accept(Quizzes.HIDE_CREATE_PANE);
    });

    save.setOnAction(e -> {
      apiBus.out().accept(new ApiRequest.Create(id.getText(), title.getText(),
        authors.getItems().stream().map(OutPerson::id).collect(Collectors.toSet()),
        inspectors.getItems().stream().map(OutPerson::id).collect(Collectors.toSet())
      ));
    });

  }

  private void clear() {
    title.setText("");
    id.setText("");
    selectedAuthor.getSelectionModel().clearSelection();
    selectedInspector.getSelectionModel().clearSelection();
  }

}
