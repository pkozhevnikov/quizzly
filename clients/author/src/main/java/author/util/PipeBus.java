package author.util;

import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.core.Observable;
import java.util.function.Consumer;

public class PipeBus<Same> implements Bus<Same, Same> {

  private final PublishSubject<Same> pipe = PublishSubject.create();

  @Override
  public Observable<Same> in() {
    return pipe;
  }

  @Override
  public Consumer<Same> out() {
    return message -> pipe.onNext(message);
  }

}
