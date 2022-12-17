package author.util;

import java.util.function.Consumer;
import io.reactivex.rxjava3.core.Observable;

public interface Bus<In, Out> {

  Observable<In> in();
  Consumer<Out> out();

}
