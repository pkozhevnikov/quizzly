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

public class HttpApiBus implements Bus<ApiResponse, ApiRequest> {

  private final String baseUrl;
  private final Bus<LoginEvent, LoginRequest> loginBus;

  private final PublishSubject<ApiResponse> subject = PublishSubject.create();


  public HttpApiBus(
    String baseUrl, 
    Bus<LoginEvent, LoginRequest> loginBus,
    Consumer<RootUIMessage> errorOut
  ) {
    this.baseUrl = baseUrl;
    this.loginBus = loginBus;
  }


  @Override
  public Consumer<ApiRequest> out() {
    return this::accept;
  }

  @Override
  public Observable<ApiResponse> in() {
    return subject;
  }

  private void accept(ApiRequest request) {

  }

}

