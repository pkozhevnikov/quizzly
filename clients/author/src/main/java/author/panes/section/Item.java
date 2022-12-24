package author.panes.section;

import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

import author.dtos.*;

import lombok.val;

public class Item extends VBox {

  public Item() {
    this(new OutItem(
      "itemSC",
      "",
      new OutStatement("some intro", null),
      List.of(
        List.of(
          new OutStatement("hint 1 alt 1", null),
          new OutStatement("hint 1 alt 2", null)
        ),
        List.of(
          new OutStatement("hint 2 alt 1", null)
        ),
        List.of(
          new OutStatement("hint 3 alt 1", null),
          new OutStatement("hint 3 alt 2", null),
          new OutStatement("hint 3 alt 3", null)
        )
      ),
      true,
      List.of(0, 2)
    ));
  }

  Item(OutItem data) {
    setSpacing(10);

    val statement = new TextArea(data.definition().text());
    val hints = new VBox();
    hints.setSpacing(10);

    int i = 0;
    for (val hint : data.hints()) {
      hints.getChildren().add(new Hint(this, i, data.solutions().contains(i++), hint));
    }

    val hintsVisible = new CheckBox("Hints visible");
    hintsVisible.setSelected(data.hintsVisible());
    val save = new Button("Save item");
    HBox buttons = new HBox();
    buttons.setSpacing(10);
    buttons.getChildren().addAll(hintsVisible, save);

    getChildren().addAll(statement, hints, buttons);
  }

  void removeHint(int index) {
    System.out.println("remove " + index);
  }

  void moveHintUp(int index) {
    System.out.println("move up " + index);
  }

  void moveHintDown(int index) {
    System.out.println("move down " + index);
  }

}
