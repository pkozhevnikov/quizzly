package author.panes.section;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Collections;
import java.util.ArrayList;
import java.util.Optional;

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
  private ScrollPane scroll;

  private Bus<ApiResponse, ApiRequest> apiBus;
  private Bus<MainUIMessage, MainUIMessage> uiBus;

  public SectionPane(Bus<ApiResponse, ApiRequest> apiBus, Bus<MainUIMessage, MainUIMessage> uiBus) {
    this.apiBus = apiBus;
    this.uiBus = uiBus;

    setId("sectionPane");

    val top = new VBox();
    top.setSpacing(10);
    top.setPadding(new Insets(20));
    title = new TextField();
    title.setId("title");
    title.setPromptText("Title");
    intro = new TextArea();
    intro.setPrefHeight(100);
    intro.setMinHeight(100);
    intro.setId("intro");
    intro.setPromptText("Intro");
    itemsBox = new VBox();
    itemsBox.setId("itemsBox");
    itemsBox.setPadding(new Insets(20));
    scroll = new ScrollPane(itemsBox);
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

    apiBus.in().ofType(ApiResponse.SectionOwned.class).subscribe(e -> setSection(e.section()));

    save.setOnAction(e -> apiBus.out().accept(new ApiRequest.UpdateSection(
      sc, title.getText(), intro.getText())));
    addItem.setOnAction(e -> apiBus.out().accept(new ApiRequest.AddItem(sc)));
    discharge.setOnAction(e -> apiBus.out().accept(new ApiRequest.DischargeSection(sc)));
    apiBus.in().ofType(ApiResponse.ItemAdded.class)
      .filter(e -> e.sectionSC().equals(sc))
      .subscribe(e -> itemsBox.getChildren().add(new Item(
        sc, new OutItem(e.sc(), "", 
          new OutStatement("", null), Collections.emptyList(), false, Collections.emptyList()),
        apiBus.out()::accept)));
    apiBus.in().ofType(ApiResponse.ItemRemoved.class)
      .filter(e -> e.sectionSC().equals(sc))
      .subscribe(e -> itemsBox.getChildren().removeIf(item -> ((Item) item).getSC().equals(e.sc())));
    apiBus.in().ofType(ApiResponse.ItemMoved.class)
      .filter(e -> e.sectionSC().equals(sc))
      .subscribe(e -> {
        val items = new ArrayList<>(itemsBox.getChildren());
        itemsBox.getChildren().clear();
        e.scs().stream().map(sc -> items.stream().map(Item.class::cast)
          .filter(i -> i.getSC().equals(sc)).findAny())
          .filter(Optional::isPresent).map(Optional::get)
          .forEach(itemsBox.getChildren()::add);
      });

  }

  private String sc;

  private void setSection(OutSection section) {
    this.sc = section.sc();
    title.setText(section.title());
    intro.setText(section.intro());
    itemsBox.getChildren().clear();
    for (val data : section.items()) {
      val item = new Item(section.sc(), data, apiBus.out());
      itemsBox.getChildren().add(item);
    }
    scroll.setVvalue(0);
  }

}
