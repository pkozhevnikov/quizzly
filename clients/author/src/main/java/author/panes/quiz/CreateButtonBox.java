package author.panes.quiz;

import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.geometry.*;

import author.util.Bus;
import author.requests.ApiRequest;
import author.events.ApiResponse;

public class CreateButtonBox extends GridPane {

  private Bus<ApiResponse, ApiRequest> apiBus;
  private Bus<Quizzes.UIMessage, Quizzes.UIMessage> uiBus;

  public CreateButtonBox(
    Bus<ApiResponse, ApiRequest> apiBus,
    Bus<Quizzes.UIMessage, Quizzes.UIMessage> uiBus
  ) {
    this.apiBus = apiBus;
    this.uiBus = uiBus;

    Button create = new Button("Create new quiz");
    create.setId("create");
    Button refresh = new Button("Refresh");
    refresh.setId("refresh");

    setPadding(new Insets(10, 10, 10, 10));
    setHgap(10);
    ColumnConstraints rightcol = new ColumnConstraints();
    rightcol.setHgrow(Priority.ALWAYS);
    getColumnConstraints().addAll(new ColumnConstraints(), rightcol);
    add(create, 0, 0);
    add(refresh, 1, 0);
    GridPane.setHalignment(refresh, HPos.RIGHT);

    create.setOnAction(e -> uiBus.out().accept(Quizzes.SHOW_CREATE_PANE));
    refresh.setOnAction(e -> apiBus.out().accept(ApiRequest.GET_LIST));

  }

}
