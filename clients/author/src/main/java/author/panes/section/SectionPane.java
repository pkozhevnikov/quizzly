package author.panes.section;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import author.dtos.*;
import author.util.*;
import author.messages.MainUIMessage;
import author.events.ApiResponse;
import author.requests.ApiRequest;

import lombok.val;


public class SectionPane extends VBox {

  private TextField title;
  private TextArea intro;
  private VBox itemsBox;

  public SectionPane(Bus<ApiResponse, ApiRequest> apiBus, Bus<MainUIMessage, MainUIMessage> uiBus) {
    setId("sectionPane");

    val top = new VBox();
    top.setSpacing(10);
    top.setPadding(new Insets(20));
    title = new TextField();
    title.setId("title");
    title.setPromptText("Title");
    intro = new TextArea();
    intro.setId("intro");
    intro.setPromptText("Intro");
    itemsBox = new VBox();
    itemsBox.setId("itemsBox");
    val scroll = new ScrollPane(itemsBox);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);

    val buttonBox = new HBox();
    buttonBox.setSpacing(10);

    val save = new Button("Save changes");
    val addItem = new Button("Add item");
    val discharge = new Button("Discharge");
    buttonBox.getChildren().addAll(save, addItem, discharge);

    top.getChildren().addAll(title, intro, buttonBox);

    getChildren().addAll(top, scroll);
    setVgrow(scroll, Priority.ALWAYS);

  }

}
