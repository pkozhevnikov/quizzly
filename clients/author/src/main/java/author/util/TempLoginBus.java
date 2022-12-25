package author.util;

import author.events.LoginEvent;
import author.requests.LoginRequest;
import author.dtos.OutPerson;

import static author.events.LoginEvent.*;
import static author.requests.LoginRequest.*;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

import java.util.function.Consumer;
import java.util.List;

import lombok.val;

public class TempLoginBus implements Bus<LoginEvent, LoginRequest> {

  private final PublishSubject<LoginEvent> subject = PublishSubject.create();

  private static List<OutPerson> staff = List.of(
    new OutPerson("author1 name", "author1"),
    new OutPerson("author2 name", "author2"),
    new OutPerson("author3 name", "author3"),
    new OutPerson("inspector1 name", "inspector1"),
    new OutPerson("inspector2 name", "inspector2"),
    new OutPerson("inspector3 name", "inspector3"),
    new OutPerson("curator name", "curator")
  );

  @Override public Observable<LoginEvent> in() {
    return subject;
  }

  private OutPerson currentlyLoggedin;

  @Override public Consumer<LoginRequest> out() {
    return r -> {
      if (r instanceof Login) {
        val lr = (Login) r;
        val person = staff.stream().filter(p -> p.id().equals(lr.username())).findAny();
        if (person.isPresent()) {
          currentlyLoggedin = person.get();
          subject.onNext(new Success(currentlyLoggedin.id(), currentlyLoggedin));
        } else {
          subject.onNext(new Failure(""));
        }
      } else if (r == LOGOUT) {
        if (currentlyLoggedin != null) {
          subject.onNext(new LoggedOut(currentlyLoggedin.id(), currentlyLoggedin));
          currentlyLoggedin = null;
        }
      }
    };
  }

}
