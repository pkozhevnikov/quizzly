package author.panes.quiz;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.*;
import java.util.stream.*;
import java.util.function.*;

import author.dtos.*;
import author.events.ApiResponse;
import author.requests.ApiRequest;
import author.util.*;
import author.messages.*;

import io.reactivex.rxjava3.core.Observable;
import static org.pdfsam.rxjavafx.observables.JavaFxObservable.*;

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
  @FXML private Button saveChanges;
  @FXML private Button setReady;
  @FXML private Button unsetReady;
  @FXML private Button approve;
  @FXML private Button disapprove;
  @FXML private TableView<OutSection> sections;
  @FXML private TableColumn<OutSection, OutSection> edit;
  @FXML private TableColumn<OutSection, String> sectionTitle;
  @FXML private TableColumn<OutSection, OutSection> up;
  @FXML private TableColumn<OutSection, OutSection> down;
  @FXML private TableColumn<OutSection, OutSection> remove;

  @Override
  public String fxml() {
    return "/author/panes/quiz/quiz-pane.fxml";
  }

  private final Bus<MainUIMessage, MainUIMessage> uiBus;
  private final Bus<ApiResponse, ApiRequest> apiBus;

  public QuizPane(
    Bus<ApiResponse, ApiRequest> apiBus,
    Bus<MainUIMessage, MainUIMessage> uiBus
  ) {
    this.apiBus = apiBus;
    this.uiBus = uiBus;
  }

  private OutPerson user;
  private OutFullQuiz quiz;

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    uiBus.in().ofType(MainUIMessage.ActingAs.class).subscribe(e -> user = e.person());
    uiBus.in().ofType(MainUIMessage.SetQuiz.class).subscribe(e -> set(e.quiz()));

    val idFactory = Factories.tableCellFactory((OutSection s) -> s);
    edit.setCellValueFactory(idFactory);
    up.setCellValueFactory(idFactory);
    down.setCellValueFactory(idFactory);
    remove.setCellValueFactory(idFactory);
    edit.setCellFactory(Factories.hyperlinkCellFactory("Edit", "edit-section", 
      (col, section) -> uiBus.out().accept(new MainUIMessage.EditSection(section))));
    up.setCellFactory(Factories.hyperlinkCellFactory("Up", "section-up",
      (col, section) -> apiBus.out().accept(new ApiRequest.MoveSection(section.sc(), true))));
    down.setCellFactory(Factories.hyperlinkCellFactory("Down", "section-down",
      (col, section) -> apiBus.out().accept(new ApiRequest.MoveSection(section.sc(), false))));
    remove.setCellFactory(Factories.hyperlinkCellFactory("Remove", "remove-section",
      (col, section) -> apiBus.out().accept(new ApiRequest.RemoveSection(section.sc(), quiz.id()))));

    sectionTitle.setCellValueFactory(Factories.tableCellFactory(OutSection::title));

    saveChanges.setOnAction(e -> {
      apiBus.out().accept(new ApiRequest.UpdateQuiz(
        title.getText(),
        intro.getText(),
        Integer.parseInt(recommendedLength.getText())
      ));
    });
    setReady.setOnAction(e -> apiBus.out().accept(new ApiRequest.SetReady(quiz.id())));
    unsetReady.setOnAction(e -> apiBus.out().accept(new ApiRequest.UnsetReady(quiz.id())));
    approve.setOnAction(e -> apiBus.out().accept(new ApiRequest.Approve(quiz.id())));
    disapprove.setOnAction(e -> apiBus.out().accept(new ApiRequest.Disapprove(quiz.id())));

    listenReadySign(apiBus.in().ofType(ApiResponse.ReadySet.class), Set::add);
    listenReadySign(apiBus.in().ofType(ApiResponse.ReadyUnset.class), Set::remove);
    listenApproval(apiBus.in().ofType(ApiResponse.Approved.class), Set::add, Set::remove);
    listenApproval(apiBus.in().ofType(ApiResponse.Disapproved.class), Set::remove, Set::add);
  }

  private <T extends ApiResponse.WithQuizId & ApiResponse.WithPersonId> void listenReadySign(
    Observable<T> in,
    BiConsumer<Set<OutPerson>, OutPerson> readyMod
  ) {
    in.filter(e -> e.quizId().equals(quiz.id())).subscribe(e ->
      quiz.authors().stream().filter(i -> i.id().equals(e.personId())).findAny()
        .ifPresent(a -> {
          val ready = new HashSet<>(quiz.readinessSigns());
          readyMod.accept(ready, a);
          set(quiz.withReadinessSigns(ready));
        })
      );
    }

  private <T extends ApiResponse.WithQuizId & ApiResponse.WithPersonId> void listenApproval(
    Observable<T> in,
    BiConsumer<Set<OutPerson>, OutPerson> approvalsMod,
    BiConsumer<Set<OutPerson>, OutPerson> disapprovalsMod
  ) {
    in.filter(e -> e.quizId().equals(quiz.id())).subscribe(e ->
      quiz.inspectors().stream().filter(i -> i.id().equals(e.personId())).findAny()
        .ifPresent(i -> {
          val apps = new HashSet<>(quiz.approvals());
          val disapps = new HashSet<>(quiz.disapprovals());
          approvalsMod.accept(apps, i);
          disapprovalsMod.accept(disapps, i);
          set(quiz.withApprovals(apps).withDisapprovals(disapps));
        })
      );
  }

  private synchronized void set(OutFullQuiz quiz) {
    this.quiz = quiz;
    id.setText(quiz.id());
    title.setText(quiz.title());
    intro.setText(quiz.intro());
    recommendedLength.setText(quiz.recommendedLength().toString());
    curatorName.setText(quiz.curator().name());
    status.setText(quiz.state());
    sections.getItems().clear();
    sections.getItems().addAll(quiz.sections());

    readiness.getChildren().clear();
    quiz.readinessSigns().stream().map(a -> {
      val l = new Label(a.name());
      l.getStyleClass().add("check");
      return l;
    }).forEach(readiness.getChildren()::add);
    approvals.getChildren().clear();
    Stream.concat(
      quiz.approvals().stream().map(i -> {
        val l = new Label(i.name());
        l.getStyleClass().add("check");
        return l;
      }),
      quiz.disapprovals().stream().map(i -> {
        val l = new Label(i.name());
        l.getStyleClass().add("uncheck");
        return l;
      })
    ).forEach(approvals.getChildren()::add);
    saveChanges.setDisable(
      (!"Composing".equals(quiz.state())) ||
      !quiz.authors().contains(user)
    );
    setReady.setDisable(
      !"Composing".equals(quiz.state()) ||
      !quiz.authors().contains(user) ||
      quiz.readinessSigns().contains(user)
    );
    unsetReady.setDisable(
      !"Composing".equals(quiz.state()) ||
      !quiz.authors().contains(user) ||
      !quiz.readinessSigns().contains(user)
    );
    approve.setDisable(
      !"Review".equals(quiz.state()) ||
      !quiz.inspectors().contains(user)
    );
    disapprove.setDisable(
      !"Review".equals(quiz.state()) ||
      !quiz.inspectors().contains(user)
    );
  }

}
  

