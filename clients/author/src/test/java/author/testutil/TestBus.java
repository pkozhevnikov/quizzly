package author.testutil;


import author.util.Bus;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.Queue;
import java.util.LinkedList;
import java.util.function.Consumer;
import org.testfx.util.*;

public class TestBus<In, Out> implements Bus<In, Out> {
  private PublishSubject<In> in = PublishSubject.create();
  private Queue<Out> outQueue = new LinkedList<>();
  @Override public Observable<In> in() { return in; }
  @Override public Consumer<Out> out() { return o -> outQueue.offer(o); }
  public Out poll() { return outQueue.poll(); }
  public TestObserver<In> emulIn(In i) {
    TestObserver<In> sub = TestObserver.create();
    in.subscribe(sub);
    javafx.application.Platform.runLater(() -> in.onNext(i));
    sub.awaitCount(1);
    sub.dispose();
    WaitForAsyncUtils.waitForFxEvents();
    return sub;
  }
}

