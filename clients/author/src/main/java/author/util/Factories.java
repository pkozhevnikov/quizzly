package author.util;

import javafx.util.Callback;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.beans.value.ObservableValue;
import javafx.beans.binding.ObjectBinding;
import java.util.function.Function;

public class Factories {

  public static <Row, Column> Callback<CellDataFeatures<Row, Column>, ObservableValue<Column>>
    cellFactory(Function<Row, Column> getter) {
    return param -> new ObjectBinding<>() {
      @Override protected Column computeValue() {
        return getter.apply(param.getValue());
      }
    };
  }

}
