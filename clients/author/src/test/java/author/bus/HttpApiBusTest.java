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

import io.reactivex.rxjava3.subscribers.TestSubscriber;

import lombok.val;

@ExtendWith(MockServerExtension.class)
public class HttpApiBusTest {

  ClientAndServer client;

  TestBus<LoginEvent, LoginRequest> loginBus = new TestBus<>();
  TestBus<RootUIMessage, RootUIMessage> uiBus = new TestBus<>();
  Bus<ApiResponse, ApiRequest> sut; 

  @BeforeEach
  void setup(ClientAndServer client) {
    this.client = client;
    sut = new HttpApiBus(
      String.format("http://localhost:%s/v1", client.getLocalPort()),
      loginBus,
      uiBus.out()
    );
  }

  @AfterEach
  void teardown() {

  }

  @Test @DisplayName("not authenticated")
  void notAuth() {
    client
      .when(request().withMethod(GET).withPath("/v1/quiz").withHeaders(header("p", "notauth")))
      .respond(response().withStatusCode(401));
    emulLoginAs(new OutPerson("notauth", ""));
    sut.out().accept(ApiRequest.GET_LIST);
    assertThat(uiBus.poll()).isEqualTo(RootUIMessage.ACCESS_DENIED);
  }

  @Test @DisplayName("not logged in")
  void notLoggedIn() {
    sut.out().accept(ApiRequest.GET_LIST);
    assertThat(uiBus.poll()).isEqualTo(RootUIMessage.NOT_LOGGED_IN);
  }

  private void emulLoginAs(OutPerson user) {
    loginBus.emulInCT(new LoginEvent.Success(user.id(), user));
  }

  @Test @DisplayName("get list")
  void getList() {
    client
      .when(request().withMethod(GET).withPath("/v1/quiz").withHeaders(header("p", "author1")))
      .respond(response().withStatusCode(200).withBody(json(TestData.list)));
    emulLoginAs(TestData.author1);
    val sub = TestSubscriber.<ApiResponse>create();
    sut.out().accept(ApiRequest.GET_LIST);
    sub.awaitCount(1);
    sub.assertValue(new ApiResponse.QuizList(TestData.list));
  }


  static String GET = "GET";
  static String POST = "POST";
  static String PUT = "PUT";
  static String PATCH = "PATCH";
  static String DELETE = "DELETE";

}

