package author.bus;

import author.events.ApiResponse;
import author.requests.ApiRequest;
import author.events.LoginEvent;
import author.requests.LoginRequest;

import static author.events.ApiResponse.*;
import static author.requests.ApiRequest.*;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

import author.util.Bus;
import author.messages.RootUIMessage;

import java.util.function.*;
import java.util.concurrent.CompletableFuture;

import java.net.http.HttpClient;

public class HttpApiBus implements Bus<ApiResponse, ApiRequest> {

  private final String baseUrl;
  private final Bus<LoginEvent, LoginRequest> loginBus;
  private final Consumer<RootUIMessage> errorOut;

  private final PublishSubject<ApiResponse> subject = PublishSubject.create();

  private author.dtos.OutPerson user;

  private final HttpClient client;

  public HttpApiBus(
    String baseUrl, 
    Bus<LoginEvent, LoginRequest> loginBus,
    Consumer<RootUIMessage> errorOut
  ) {
    this.baseUrl = baseUrl;
    this.loginBus = loginBus;
    this.errorOut = errorOut;
    loginBus.in().ofType(LoginEvent.Success.class).subscribe(e -> user = e.user());
    loginBus.in().ofType(LoginEvent.LoggedOut.class)
      .filter(e -> user == null || user.equals(e.user()))
      .subscribe(e -> user = null);

    client = HttpClient.newBuilder()
      build();
  }


  @Override
  public Consumer<ApiRequest> out() {
    return r -> {
      if (user == null)
        errorOut.accept(RootUIMessage.NOT_LOGGED_IN);
      else
        process(r).thenAccept(resp -> {
          if (resp != NO_RESPONSE) subject.onNext(resp);
        });
    };
  }

  @Override
  public Observable<ApiResponse> in() {
    return subject;
  }

  private static final ApiResponse NO_RESPONSE = new ApiResponse() {};

  private CompletableFuture<ApiResponse> process(ApiRequest request) {
    
    errorOut.accept(RootUIMessage.ACCESS_DENIED);
    return CompletableFuture.completedFuture(NO_RESPONSE);
  }

  private static class CallDescriptor<Req, Res> {
    private Function<Req, HttpRequest> request;
    private Map<Integer, BiFunction<Req, InputStream, Res>> responses;
  }

}

