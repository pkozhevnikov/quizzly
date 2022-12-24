package author;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;

import javafx.application.Platform;

public class FormViewer extends Application {

  @Override
  public void start(Stage stage) throws Exception {
    String path = getParameters().getUnnamed().get(0);
    Node node = null;
    if (path.contains(".")) {
      try {
        node = (Node) Class.forName(path).newInstance();
      } catch (Exception ex) {
        ex.printStackTrace();
        Platform.exit();
      }
    } else {
      FXMLLoader loader = new FXMLLoader(getClass().getResource(path + ".fxml"));
      loader.setController(new NopController());
      node = loader.load();
    }

    StackPane pane = new StackPane(node);
    Scene scene = new Scene(pane, 1000, 700);
    scene.getStylesheets().add("/author/common.css");
    stage.setScene(scene);
    stage.setTitle("form viewer");
    stage.show();
  }

  static class NopController implements javafx.fxml.Initializable {
    @Override
    public void initialize(
      java.net.URL l,
      java.util.ResourceBundle r
    ) {}
  }

}

