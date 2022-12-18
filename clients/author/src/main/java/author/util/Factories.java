package author.util;

import javafx.fxml.FXMLLoader;
import javafx.util.Callback;
import javafx.scene.Node;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.*;
import javafx.geometry.*;
import javafx.beans.value.ObservableValue;
import javafx.beans.binding.ObjectBinding;
import java.util.function.*;

import lombok.val;

@lombok.extern.slf4j.Slf4j
public class Factories {

  public static <Row, Column> Callback<CellDataFeatures<Row, Column>, ObservableValue<Column>>
    tableCellFactory(Function<Row, Column> getter) {
    return param -> new ObjectBinding<>() {
      @Override protected Column computeValue() {
        return getter.apply(param.getValue());
      }
    };
  }

  public static <E> Callback<ListView<E>, ListCell<E>> listCellFactory(Function<E, String> getter) {
    return ignore -> new ListCell<>() {
      @Override
      protected void updateItem(E item, boolean empty) {
        super.updateItem(item, empty);
        if (item == null || empty) {
          setText("");
        } else {
          setText(getter.apply(item));
        }
      }
    };
  }

  public static <E> Callback<ListView<E>, ListCell<E>> 
      listCellFactory(Function<E, String> getter, String buttonCaption,
        BiConsumer<ListView<E>, E> onButtonClick) {
    return list -> new ListCell<>() {
      Hyperlink button;
      {
        button = new Hyperlink(buttonCaption);
        button.getStyleClass().add("remove-item");
        button.setOnAction(e -> onButtonClick.accept(list, getItem()));
        setOnMouseEntered(e -> button.setVisible(true));
        setOnMouseExited(e -> button.setVisible(false));
        button.setVisible(false);
      }
      @Override
      protected void updateItem(E item, boolean empty) {
        super.updateItem(item, empty);
        if (item == null || empty) {
          setText("");
          setGraphic(null);
        } else {
          setText(getter.apply(item));
          setGraphic(button);
        }
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
