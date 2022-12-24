package author.panes.section;

import javafx.scene.control.*;
import javafx.scene.*;
import javafx.scene.layout.*;
import javafx.geometry.*;
import javafx.beans.value.*;

import author.dtos.*;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class Hint extends HBox {

  private CheckBox solution;
  private VBox statements;

  private int index;

  public Hint(Item item, int index, boolean isSolution, List<OutStatement> alternatives) {
    this.index = index;
    getStyleClass().add("hint");
    setAlignment(Pos.TOP_LEFT);
    setSpacing(10);
    solution = new CheckBox();
    solution.setSelected(isSolution);
    setIndex(index);
    statements = new VBox();
    getChildren().addAll(solution, statements);
    setHgrow(statements, Priority.ALWAYS);

    ContextMenu menu = new ContextMenu();
    MenuItem moveUp = new MenuItem("Move up");
    moveUp.setOnAction(e -> item.moveHintUp(getIndex()));
    MenuItem moveDown = new MenuItem("Move down");
    moveDown.setOnAction(e -> item.moveHintDown(getIndex()));
    MenuItem remove = new MenuItem("Remove");
    remove.setOnAction(e -> item.removeHint(getIndex()));
    menu.getItems().addAll(moveUp, moveDown, new SeparatorMenuItem(), remove);

    solution.setContextMenu(menu);

    setStatements(alternatives);
  }

  void setIndex(int index) {
    this.index = index;
    solution.setText(String.valueOf(index + 1));
  }

  int getIndex() {
    return index;
  }
  
  private void setStatements(final List<OutStatement> stmts) {
    statements.getChildren().clear();
    List<OutStatement> insert = new ArrayList<>(stmts);
    if (insert.isEmpty())
      insert.add(new OutStatement("", null));
    boolean first = true;
    for (OutStatement s : insert) {
      Statement control = new Statement(this, first);
      control.text.setText(s.text());
      first = false;
      statements.getChildren().add(control);
    }
  }

  private List<OutStatement> getAlternatives() {
    return statements.getChildren().stream()
      .map(s -> new OutStatement(((Statement) s).text.getText(), null))
      .collect(Collectors.toList());
  }

  private void hintClicked(Statement stmt) {
    if (statements.getChildren().indexOf(stmt) == 0) {
      statements.getChildren().add(new Statement(this, false));
    } else {
      statements.getChildren().remove(stmt);
    }
  }


  private static class Statement extends HBox {

    private TextField text;
    private Button button;
    private Hint hint;

    private Statement(Hint hint, boolean first) {
      this.hint = hint;
      getStyleClass().add("hint-alternative");
      text = new TextField();
      setHgrow(text, Priority.ALWAYS);
      button = new Button();
      button.getStyleClass().add(first ? "plus" : "minus");
      getChildren().addAll(text, button);
      setHgrow(text, Priority.ALWAYS);
      button.setOnAction(e -> hint.hintClicked(this));
    }
  }

}
