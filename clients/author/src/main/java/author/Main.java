package author;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;

import author.util.*;
import author.bus.HttpApiBus;
import author.panes.RootPane;
import author.messages.RootUIMessage;

import lombok.val;

public class Main extends Application {

  @Override public void start(Stage stage) throws Exception {
    if (getParameters().getUnnamed().isEmpty()) {
      System.err.println("Server URL not specified");
      System.exit(1);
    }
    val baseUrl = getParameters().getUnnamed().get(0);
    val loginBus = new TempLoginBus();
    val rootUiBus = new PipeBus<RootUIMessage>();
    val apiBus = new HttpApiBus(
      baseUrl,
      loginBus,
      rootUiBus.out()
    );

    val rootPane = new RootPane(apiBus, loginBus, rootUiBus);
    
    Scene scene = new Scene(rootPane, 1000, 700);
    scene.getStylesheets().add("/author/common.css");
    stage.setScene(scene);
    stage.setTitle("Quizzly::author");
    stage.show();
  }

}
