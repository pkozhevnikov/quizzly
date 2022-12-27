package author.testutil;


import author.util.Bus;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.testfx.util.*;

public class TestBus<In, Out> implements Bus<In, Out> {
  private PublishSubject<In> in = PublishSubject.create();
  private Queue<Out> outQueue = new LinkedList<>();
  @Override public Observable<In> in() { return in; }

  private Map<Out, Response> responses = new ConcurrentHashMap<>();
  
  @Override public Consumer<Out> out() { 
    return o -> {
      outQueue.offer(o); 
      if (responses.containsKey(o)) {
        //emulIn(responses.get(o).emit);
        javafx.application.Platform.runLater(() -> in.onNext(responses.get(o).emit));
      }
    };
  }

  public Out poll() { return outQueue.poll(); }
  public Out poll(int count) {
    for (int i = 1; i < count - 1; i++) outQueue.poll();
    return outQueue.poll();
  }
  public void emulInCT(In i) {
    in.onNext(i);
  }
  public TestObserver<In> emulIn(In i) {
    TestObserver<In> sub = TestObserver.create();
    in.subscribe(sub);
    javafx.application.Platform.runLater(() -> in.onNext(i));
    sub.awaitCount(1);
    sub.dispose();
    WaitForAsyncUtils.waitForFxEvents();
    return sub;
  }

  public Response on(Out request, In response) {
    Response resp = new Response();
    resp.on = request;
    resp.emit = response;
    responses.put(request, resp);
    return resp;
  }

  public class Response {
    Out on;
    In emit;
    public void free() {
      TestBus.this.responses.remove(on);
    }
  }
}

