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

  public static <Row, Column> Callback<TableColumn<Row, Column>, TableCell<Row, Column>>
    hyperlinkCellFactory(String buttonCaption, 
          String buttonClass, BiConsumer<TableColumn<Row, Column>, Column> onClick) {
      return column -> new TableCell<>() {
        Hyperlink button;
        {
          button = new Hyperlink(buttonCaption);
          button.getStyleClass().add(buttonClass);
          button.setOnAction(e -> onClick.accept(column, getItem()));
          button.setPadding(new Insets(0, 0, 0, 0));
          tableRowProperty().addListener((p, o, n) -> {
            if (n != null) {
              n.setOnMouseEntered(e -> button.setVisible(true));
              n.setOnMouseExited(e -> button.setVisible(false));
            }
          });
          button.setVisible(false);
        }
        @Override
        protected void updateItem(Column item, boolean empty) {
          super.updateItem(item, empty);
          setText(null);
          if (item == null || empty) {
            setGraphic(null);
          } else {
            setGraphic(button);
          }
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
      listCellFactory(Function<E, String> getter, String buttonCaption, String linkClassName,
        BiConsumer<ListView<E>, E> onButtonClick) {
    return list -> new ListCell<>() {
      Hyperlink button;
      {
        button = new Hyperlink(buttonCaption);
        button.getStyleClass().add(linkClassName);
        button.setOnAction(e -> onButtonClick.accept(list, getItem()));
        button.setPadding(new Insets(0, 0, 0, 0));
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
