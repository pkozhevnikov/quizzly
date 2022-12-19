package author.panes.quiz;

import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.geometry.*;
import javafx.scene.layout.*;

import author.util.Bus;
import author.dtos.*;
import author.events.ApiResponse;
import author.requests.ApiRequest;
import author.util.*;

public class Quizzes {

  interface UIMessage {}
  static final UIMessage CLEAR_CREATE_PANE = new UIMessage() {};
  static final UIMessage SHOW_CREATE_PANE = new UIMessage() {};
  static final UIMessage HIDE_CREATE_PANE = new UIMessage() {};
  @lombok.Value
  static class ShowQuiz implements UIMessage {
    OutQuizListed quiz;
  }
  @lombok.Value
  static class GotoQuiz implements UIMessage {
    String quizId;
  }

  private final Bus<UIMessage, UIMessage> uiBus = new PipeBus<>();
  private final Bus<ApiResponse, ApiRequest> apiBus;

  private Quizzes(Bus<ApiResponse, ApiRequest> apiBus) {
    this.apiBus = apiBus;
  }

  private Node listPane;
  private Node createPane;
  private Node form;
  private Node buttonBox;

  public static Node create(Bus<ApiResponse, ApiRequest> apiBus) {
    return new Quizzes(apiBus).init();
  }

  private Node init() {

    Node side = Factories.nodeWith(new QuizFormPane(apiBus, uiBus));
    side.setId("form");
    Node list = Factories.nodeWith(new ListPane(apiBus, uiBus));
    list.setId("mainList");
    Node create = Factories.nodeWith(new CreateQuizPane(apiBus, uiBus));
    create.setId("createPane");
    Node buttons = new CreateButtonBox(apiBus, uiBus);
    buttons.setId("buttonsBox");

    SplitPane root = new SplitPane();
    VBox main = new VBox();
    main.getChildren().addAll(list, buttons);
    root.getItems().addAll(main, side);
    root.setDividerPositions(.7);


    uiBus.in().filter(m -> m == SHOW_CREATE_PANE).subscribe(m -> {
      main.getChildren().remove(buttons);
      main.getChildren().add(create);
    });
    uiBus.in().filter(m -> m == HIDE_CREATE_PANE).subscribe(m -> {
      main.getChildren().remove(create);
      main.getChildren().add(buttons);
    });

    uiBus.out().accept(new ShowQuiz(null));
    uiBus.out().accept(CLEAR_CREATE_PANE);


    return root;
  }


}
