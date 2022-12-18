package author.util;

import javafx.fxml.FXMLLoader;
import javafx.util.Callback;
import javafx.scene.Node;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.beans.value.ObservableValue;
import javafx.beans.binding.ObjectBinding;
import java.util.function.Function;

@lombok.extern.slf4j.Slf4j
public class Factories {

  public static <Row, Column> Callback<CellDataFeatures<Row, Column>, ObservableValue<Column>>
    cellFactory(Function<Row, Column> getter) {
    return param -> new ObjectBinding<>() {
      @Override protected Column computeValue() {
        return getter.apply(param.getValue());
      }
    };
  }

  public static Node nodeWith(FxmlController controller) {
    try {
      FXMLLoader loader = new FXMLLoader(Factories.class.getResource(controller.fxml()));
      loader.setController(controller);
      return loader.load();
    } catch (Exception ex) {
      log.error("cannot create node with controller " + controller.getClass() +
        " (" + controller.fxml() + ")", ex);
      return null;
    }
  }
    
}
