package author.bus;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.*;
import java.util.stream.*;
import java.util.function.*;
import java.util.concurrent.*;

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
import org.mockserver.model.*;

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
    this.client.reset();
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
    apiSubscriber.await(300, TimeUnit.MILLISECONDS);
    apiSubscriber.assertNoValues();
  }

  @Test @DisplayName("not logged in")
  void notLoggedIn() throws Exception {
    sut.out().accept(ApiRequest.GET_LIST);
    uiSubscriber.awaitCount(1);
    uiSubscriber.assertValue(RootUIMessage.NOT_LOGGED_IN);
    apiSubscriber.await(300, TimeUnit.MILLISECONDS);
    apiSubscriber.assertNoValues();
  }

  @Test @DisplayName("processing error")
  void processingError() throws Exception {
    client.when(request().withMethod(GET).withPath("/v1/quiz").withHeaders(header("p", "author1")))
      .respond(response().withStatusCode(200).withBody("{"));
    emulLoginAs(TestData.author1);
    sut.out().accept(ApiRequest.GET_LIST);
    apiSubscriber.await(300, TimeUnit.MILLISECONDS);
    apiSubscriber.assertNoValues();
    uiSubscriber.awaitCount(1);
    assertThat(uiSubscriber.values().get(0)).isInstanceOf(RootUIMessage.ProcessingError.class);
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

  @Test @DisplayName("get full quiz")
  void getQuiz() {
    client
      .when(request().withMethod(GET).withPath("/v1/quiz/q1").withHeaders(header("p", "author1")))
      .respond(response().withStatusCode(200).withBody(toJson(TestData.fullQuiz1)));
    emulLoginAs(TestData.author1);
    sut.out().accept(new ApiRequest.GetQuiz("q1"));
    apiSubscriber.awaitCount(1);
    apiSubscriber.assertValue(new ApiResponse.FullQuiz(TestData.fullQuiz1));
  }

  @Test @DisplayName("create quiz")
  void createQuiz() {
    val resp = new OutCreateDetails(Set.of(TestData.author1, TestData.author2),
          Set.of(TestData.inspector1, TestData.inspector2));
    val authorsIds = Set.of(TestData.author1.id(), TestData.author2.id());
    val inspectorsIds = Set.of(TestData.inspector1.id(), TestData.inspector2.id());
    client
      .when(request()
        .withMethod(POST).withPath("/v1/quiz")
        .withHeader(header("p", "curator"))
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(toJson(new InCreateQuiz("q55", "new quiz title", "", 30, authorsIds, inspectorsIds)))
      )
      .respond(response().withStatusCode(200).withBody(toJson(resp)));
    emulLoginAs(TestData.curator);
    sut.out().accept(new ApiRequest.Create("q55", "new quiz title", authorsIds, inspectorsIds));
    apiSubscriber.awaitCount(1);
    apiSubscriber.assertValue(new ApiResponse.QuizAdded(new OutQuizListed("q55", "new quiz title",
      false, TestData.curator, resp.authors(), resp.inspectors(), "Composing")));
  }
    
  @Test @DisplayName("get staff")
  void getStaff() {
    val staff = List.of(TestData.curator, TestData.author1, TestData.author2, TestData.author3,
        TestData.inspector1, TestData.inspector2, TestData.inspector3);
    client
      .when(request().withMethod(GET).withPath("/v1/staff").withHeaders(header("p", "inspector3")))
      .respond(response().withStatusCode(200).withBody(toJson(staff)));
    emulLoginAs(TestData.inspector3);
    sut.out().accept(ApiRequest.GET_STAFF);
    apiSubscriber.awaitCount(1);
    apiSubscriber.assertValue(new ApiResponse.PersonList(staff));
  }

  static String GET = "GET";
  static String POST = "POST";
  static String PUT = "PUT";
  static String PATCH = "PATCH";
  static String DELETE = "DELETE";

}

