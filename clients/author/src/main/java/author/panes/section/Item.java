package author.panes.section;

import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;

import author.dtos.*;
import author.requests.ApiRequest;

import lombok.val;

public class Item extends VBox {


  public Item(String sectionSC, OutItem data, Consumer<ApiRequest> apiCommands) {
    setSpacing(10);

    definition = new TextArea();
    definition.setId("definition");
    hints = new VBox();
    hints.setId("hints");
    hints.setSpacing(10);


    hintsVisible = new CheckBox("Hints visible");
    hintsVisible.setId("hintsVisible");
    val addHint = new Button("Add hint");
    val save = new Button("Save item");
    val moveUp = new Button("Move up");
    val moveDown = new Button("Move down");
    val remove = new Button("Remove");
    HBox buttons = new HBox();
    buttons.setSpacing(10);
    buttons.getChildren().addAll(hintsVisible, addHint, save, moveUp, moveDown, remove);

    getChildren().addAll(definition, hints, buttons);

    addHint.setOnAction(e -> addHint());
    save.setOnAction(e -> apiCommands.accept(new ApiRequest.SaveItem(this.sectionSC, getItem())));
    remove.setOnAction(e -> apiCommands.accept(new ApiRequest.RemoveItem(this.sectionSC, sc)));
    moveUp.setOnAction(e -> apiCommands.accept(new ApiRequest.MoveItem(this.sectionSC, sc, true)));
    moveDown.setOnAction(e -> apiCommands.accept(new ApiRequest.MoveItem(this.sectionSC, sc, false)));

    setData(sectionSC, data);
  }

  private String sectionSC;
  private String sc;

  private OutItem getItem() {
    val solutions = new ArrayList<Integer>();
    val hintLists = new ArrayList<List<OutStatement>>();
    int index = 0;
    for (val c : hints.getChildren()) {
      val hint = (Hint) c;
      if (hint.isSolution())
        solutions.add(index++);
      hintLists.add(hint.getAlternatives());
    }
    return new OutItem(
      sc,
      "",
      new OutStatement(definition.getText(), null),
      hintLists,
      hintsVisible.isSelected(),
      solutions
    );
  }

  void setData(String sectionSC, OutItem data) {
    this.sectionSC = sectionSC;
    sc = data.sc();
    definition.setText(data.definition().text());
    hintsVisible.setSelected(data.hintsVisible());
    hints.getChildren().clear();
    int i = 0;
    for (val hint : data.hints()) {
      hints.getChildren().add(new Hint(this, i, data.solutions().contains(i++), hint));
    }
  }

  private void addHint() {
    hints.getChildren().add(new Hint(this, hints.getChildren().size(), false, List.of()));
  }

  private TextArea definition;
  private VBox hints;
  private CheckBox hintsVisible;

  void removeHint(int index) {
    hints.getChildren().remove(index);
    updateIndexes();
  }

  private void updateIndexes() {
    int i = 0;
    for (val c : hints.getChildren())
      ((Hint) c).setIndex(i++);
  }

  void moveHintUp(int index) {
    if (index == 0) return;
    val c = hints.getChildren().get(index);
    hints.getChildren().remove(index);
    hints.getChildren().add(index - 1, c);
    updateIndexes();
  }

  void moveHintDown(int index) {
    if (index == hints.getChildren().size() - 1) return;
    val c = hints.getChildren().get(index);
    hints.getChildren().remove(index);
    hints.getChildren().add(index + 1, c);
    updateIndexes();
  }

}
