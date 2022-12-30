package author;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;

import author.util.*;
import author.bus.HttpApiBus;
import author.panes.RootPane;
import author.messages.RootUIMessage;

import lombok.val;

@lombok.extern.slf4j.Slf4j
public class Main extends Application {

  private PreviewServer previewServer;

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


    val previewPort = PreviewServer.freePort();
    previewServer = new PreviewServer(apiBus);
    previewServer.start(previewPort);
    rootUiBus.in().ofType(RootUIMessage.PreviewQuiz.class).subscribe(e -> {
      try {
        new ProcessBuilder("firefox", "http://localhost:" + previewPort + 
          "/preview?qid=" + e.quizId()).start();
      } catch (Exception ex) {
        log.error("cannot run browser", ex);
      }
    });


    val rootPane = new RootPane(apiBus, loginBus, rootUiBus);
    
    Scene scene = new Scene(rootPane, 1000, 700);
    scene.getStylesheets().add("/author/common.css");
    stage.setScene(scene);
    stage.setTitle("Quizzly::author");
    stage.show();
  }

  @Override public void stop() throws Exception {
    previewServer.stop();
    super.stop();
  }

}
