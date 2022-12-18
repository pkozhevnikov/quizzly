package author.util;

import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.core.Observable;
import java.util.function.Consumer;

public class SubjectBus<In, Out> implements Bus<In, Out> {

  private final PublishSubject<Out> out = PublishSubject.create();
  private final PublishSubject<In> in = PublishSubject.create();

  @Override
  public Observable<In> in() {
    return in;
  }

  @Override
  public Consumer<Out> out() {
    return message -> out.onNext(message);
  }

}
