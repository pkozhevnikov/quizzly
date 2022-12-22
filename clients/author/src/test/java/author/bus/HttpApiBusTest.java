package author.bus;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.*;
import java.util.stream.*;
import java.util.function.*;

import org.assertj.core.api.*;
import static org.assertj.core.api.Assertions.*;

import author.events.*;
import author.requests.*;
import author.util.*;
import author.dtos.*;
import author.messages.*;

import author.testutil.*;
import author.TestData;

import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.integration.ClientAndServer;
import static org.mockserver.model.HttpRequest.*;
import static org.mockserver.model.HttpResponse.*;
import static org.mockserver.model.Header.*;
import static org.mockserver.model.JsonBody.*;

import io.reactivex.rxjava3.observers.TestObserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.DefaultAccessorNamingStrategy;

import lombok.val;

@ExtendWith(MockServerExtension.class)
public class HttpApiBusTest {

  ObjectMapper mapper = new ObjectMapper() {{
    setAccessorNaming(new DefaultAccessorNamingStrategy.Provider().withGetterPrefix(""));
  }};

  ClientAndServer client;

  TestBus<LoginEvent, LoginRequest> loginBus = new TestBus<>();
  Bus<RootUIMessage, RootUIMessage> uiBus = new PipeBus<>();
  TestObserver<RootUIMessage> uiSubscriber = TestObserver.create();
  Bus<ApiResponse, ApiRequest> sut; 
  TestObserver<ApiResponse> apiSubscriber = TestObserver.create();

  @BeforeEach
  void setup(ClientAndServer client) {
    this.client = client;
    sut = new HttpApiBus(
      String.format("http://localhost:%s/v1", client.getLocalPort()),
      loginBus,
      uiBus.out()
    );
    sut.in().subscribe(apiSubscriber);
    uiBus.in().subscribe(uiSubscriber);
  }

  @AfterEach
  void teardown() {
    apiSubscriber.dispose();
    uiSubscriber.dispose();
  }

  @Test @DisplayName("not authenticated")
  void notAuth() throws Exception {
    client
      .when(request().withMethod(GET).withPath("/v1/quiz").withHeaders(header("p", "notauth")))
      .respond(response().withStatusCode(401));
    emulLoginAs(new OutPerson("noname", "notauth"));
    sut.out().accept(ApiRequest.GET_LIST);
    uiSubscriber.awaitCount(1);
    uiSubscriber.assertValue(RootUIMessage.ACCESS_DENIED);
  }

  @Test @DisplayName("not logged in")
  void notLoggedIn() throws Exception {
    sut.out().accept(ApiRequest.GET_LIST);
    uiSubscriber.awaitCount(1);
    uiSubscriber.assertValue(RootUIMessage.NOT_LOGGED_IN);
  }

  private void emulLoginAs(OutPerson user) {
    loginBus.emulInCT(new LoginEvent.Success(user.id(), user));
  }

  private String toJson(Object object) {
    try {
      return mapper.writeValueAsString(object);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Test @DisplayName("get list")
  void getList() throws Exception {
    client
      .when(request().withMethod(GET).withPath("/v1/quiz").withHeaders(header("p", "author1")))
      .respond(response().withStatusCode(200).withBody(toJson(TestData.list)));
    emulLoginAs(TestData.author1);
    sut.out().accept(ApiRequest.GET_LIST);
    apiSubscriber.awaitCount(1);
    apiSubscriber.assertValue(new ApiResponse.QuizList(TestData.list));
  }


  static String GET = "GET";
  static String POST = "POST";
  static String PUT = "PUT";
  static String PATCH = "PATCH";
  static String DELETE = "DELETE";

}

