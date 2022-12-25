package author.panes.quiz;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.*;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.Optional;
import java.util.*;
import java.util.function.*;

import author.dtos.*;
import author.events.ApiResponse;
import author.requests.ApiRequest;
import author.util.*;
import author.util.Tuple.*;

import io.reactivex.rxjava3.core.Observable;

import lombok.val;

public class ListPane implements FxmlController {

  @FXML private TableView<OutQuizListed> list;
  @FXML private TableColumn<OutQuizListed, String> link;
  @FXML private TableColumn<OutQuizListed, String> id;
  @FXML private TableColumn<OutQuizListed, String> title;
  @FXML private TableColumn<OutQuizListed, String> status;
  @FXML private TableColumn<OutQuizListed, String> obsolete;

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

  private final List<OutPerson> staff = new ArrayList<>();

  @Override
  public void initialize(URL location, ResourceBundle resource) {
    
      list.getItems().clear();
      val idFactory = Factories.tableCellFactory(OutQuizListed::id);
      link.setCellValueFactory(idFactory);
      link.setCellFactory(Factories.hyperlinkCellFactory("Goto", "goto-quiz", (column, id) ->
        apiBus.out().accept(new ApiRequest.GetQuiz(id))));
      id.setCellValueFactory(idFactory);
      title.setCellValueFactory(Factories.tableCellFactory(OutQuizListed::title));
      status.setCellValueFactory(Factories.tableCellFactory(OutQuizListed::state));
      obsolete.setCellValueFactory(Factories.tableCellFactory(q -> q.obsolete() ? "+" : ""));
      apiBus.in().ofType(ApiResponse.PersonList.class)
        .subscribe(l -> {
          staff.clear();
          staff.addAll(l.list());
        });
      apiBus.in().ofType(ApiResponse.QuizList.class)
        .subscribe(l -> {
          list.getItems().clear();
          list.getItems().addAll(l.list());
        });

      list.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
        uiBus.out().accept(new Quizzes.ShowQuiz(nv));
      });
      apiBus.in().ofType(ApiResponse.QuizAdded.class)
        .subscribe(q -> {
          list.getItems().add(q.quiz());
          list.getSelectionModel().select(q.quiz());
        });

      val withQuiz = apiBus.in().ofType(ApiResponse.WithQuizId.class)
        .map(e -> list.getItems().stream()
                    .filter(q -> q.id().equals(e.quizId())).findFirst()
                    .map(q -> Tuple.of(e, q)))
        .filter(Optional::isPresent)
        .map(Optional::get);
      
      val obsolete = withQuiz
        .filter(wq -> wq._1() instanceof ApiResponse.GotObsolete)
        .map(wq -> Tuple.of(wq._2(), (UnaryOperator<OutQuizListed>) q -> q.withObsolete(true)));

      BiFunction<Set<OutPerson>, OutPerson, Set<OutPerson>> remove = (set, pers) -> {
        val nset = new HashSet<>(set);
        nset.remove(pers);
        return Collections.unmodifiableSet(nset);
      };
      BiFunction<Set<OutPerson>, OutPerson, Set<OutPerson>> add = (set, pers) -> {
        val nset = new HashSet<>(set);
        nset.add(pers);
        return Collections.unmodifiableSet(nset);
      };

      val addAuth = forPersonedType(ApiResponse.AuthorAdded.class, withQuiz,
        p -> q -> q.withAuthors(add.apply(q.authors(), p)));
      val remAuth = forPersonedType(ApiResponse.AuthorRemoved.class, withQuiz,
        p -> q -> q.withAuthors(remove.apply(q.authors(), p)));
      val addInsp = forPersonedType(ApiResponse.InspectorAdded.class, withQuiz,
        p -> q -> q.withInspectors(add.apply(q.inspectors(), p)));
      val remInsp = forPersonedType(ApiResponse.InspectorRemoved.class, withQuiz,
        p -> q -> q.withInspectors(remove.apply(q.inspectors(), p)));

      Observable.mergeArray(obsolete, addAuth, remAuth, addInsp, remInsp).subscribe(ft -> {
        val idx = list.getItems().indexOf(ft._1());
        list.getItems().set(idx, ft._2().apply(ft._1()));
      });


  }

  @SuppressWarnings("unchecked")
  private <Typ extends ApiResponse.WithPersonId> 
    Observable<Tuple2<OutQuizListed, UnaryOperator<OutQuizListed>>>
      forPersonedType(Class<Typ> clazz, 
        Observable<Tuple2<ApiResponse.WithQuizId, OutQuizListed>> withQuiz,
          Function<OutPerson, UnaryOperator<OutQuizListed>> memberMod) {
    return withQuiz
      .filter(wq -> clazz.isInstance(wq._1()))
      .map(wq -> staff.stream()
              .filter(p -> p.id().equals(((Typ) wq._1()).personId())).findFirst()
          .map(p -> Tuple.of(wq._2(), memberMod.apply(p)))
          .orElse(Tuple.of(wq._2(), UnaryOperator.identity()))
      );
  }

}
