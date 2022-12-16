package author;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;

public class FormViewer extends Application {

  @Override
  public void start(Stage stage) throws Exception {
    String path = getParameters().getUnnamed().get(0);
    FXMLLoader loader = new FXMLLoader(getClass().getResource(path + ".fxml"));
    loader.setController(new NopController());
    Node node = loader.load();

    StackPane pane = new StackPane(node);
    stage.setScene(new Scene(pane, 1000, 700));
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

