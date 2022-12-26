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

public class QuizFormPane implements FxmlController {

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
  public String fxml() {
    return "/author/panes/quiz/quiz-form.fxml";
  }

  private final Bus<ApiResponse, ApiRequest> apiBus;
  private final Bus<Quizzes.UIMessage, Quizzes.UIMessage> uiBus;

  public QuizFormPane(
    Bus<ApiResponse, ApiRequest> apiBus,
    Bus<Quizzes.UIMessage, Quizzes.UIMessage> uiBus
  ) {
    this.apiBus = apiBus;
    this.uiBus = uiBus;
  }

  @Override
  public void initialize(URL location, ResourceBundle resource) {
    
    val comboCellFactory = Factories.listCellFactory(OutPerson::name);
    val listCellFactory = Factories.buttonListCellFactory(OutPerson::name, "trash-bold", 
                "remove-item", (list, p) -> {
        if (list == authors)
          apiBus.out().accept(new ApiRequest.RemoveAuthor(current.id(), p.id()));
        else
          apiBus.out().accept(new ApiRequest.RemoveInspector(current.id(), p.id()));
      });
    authors.setCellFactory(listCellFactory);
    selectedAuthor.setButtonCell(comboCellFactory.call(null));
    selectedAuthor.setCellFactory(comboCellFactory);
    inspectors.setCellFactory(listCellFactory);
    selectedInspector.setCellFactory(comboCellFactory);
    selectedInspector.setButtonCell(comboCellFactory.call(null));
    apiBus.in().ofType(ApiResponse.PersonList.class)
      .subscribe(l -> {
        selectedAuthor.getItems().clear();
        selectedAuthor.getItems().addAll(l.list());
        selectedInspector.getItems().clear();
        selectedInspector.getItems().addAll(l.list());
      });

    uiBus.in().ofType(Quizzes.ShowQuiz.class).subscribe(s -> {
      if (s.quiz() == null)
        clear();
      else
        set(s.quiz());
    });

    apiBus.in().ofType(ApiResponse.AuthorAdded.class)
      .filter(s -> current != null)
      .filter(s -> current.id().equals(s.quizId()))
      .subscribe(s -> {
        selectedAuthor.getItems().stream().filter(a -> a.id().equals(s.personId())).findAny()
          .ifPresent(authors.getItems()::add);
      });

    apiBus.in().ofType(ApiResponse.AuthorRemoved.class)
      .filter(s -> current != null)
      .filter(s -> current.id().equals(s.quizId()))
      .subscribe(s -> authors.getItems().removeIf(a -> a.id().equals(s.personId())));
    addAuthor.setOnAction(e -> apiBus.out().accept(
      new ApiRequest.AddAuthor(current.id(), selectedAuthor.getValue().id())));

    apiBus.in().ofType(ApiResponse.InspectorAdded.class)
      .filter(s -> current != null)
      .filter(s -> current.id().equals(s.quizId()))
      .subscribe(s -> {
        selectedInspector.getItems().stream().filter(a -> a.id().equals(s.personId())).findAny()
          .ifPresent(inspectors.getItems()::add);
      });

    apiBus.in().ofType(ApiResponse.InspectorRemoved.class)
      .filter(s -> current != null)
      .filter(s -> current.id().equals(s.quizId()))
      .subscribe(s -> inspectors.getItems().removeIf(a -> a.id().equals(s.personId())));
    addInspector.setOnAction(e -> apiBus.out().accept(
      new ApiRequest.AddInspector(current.id(), selectedInspector.getValue().id())));

    apiBus.in().ofType(ApiResponse.GotObsolete.class)
      .filter(s -> current != null)
      .filter(s -> current.id().equals(s.quizId()))
      .subscribe(s -> set(current.withObsolete(true)));
    setObsolete.setOnAction(e -> apiBus.out().accept(new ApiRequest.SetObsolete(current.id())));
      
  }

  private OutQuizListed current = null;

  private void clear() {
    current = null;
    curatorName.setText("");
    status.setText("");
    authors.getItems().clear();
    inspectors.getItems().clear();
    selectedAuthor.getSelectionModel().clearSelection();
    selectedInspector.getSelectionModel().clearSelection();
    setObsolete.setDisable(true);
    addAuthor.setDisable(true);
    addInspector.setDisable(true);
  }

  private void set(OutQuizListed quiz) {
    current = quiz;
    curatorName.setText(quiz.curator().name());
    status.setText(quiz.state());
    authors.getItems().clear();
    authors.getItems().addAll(quiz.authors());
    inspectors.getItems().clear();
    inspectors.getItems().addAll(quiz.inspectors());
    setObsolete.setDisable(quiz.obsolete() || !quiz.state().equals("Released"));
    addAuthor.setDisable(false);
    addInspector.setDisable(false);
  }

}
