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

  private void assertNoUiEvents() {
    try {
      uiSubscriber.await(300, TimeUnit.MILLISECONDS);
      uiSubscriber.assertNoValues();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private void assertUiEvent(RootUIMessage event) {
    uiSubscriber.awaitCount(1);
    uiSubscriber.assertValue(event);
  }

  private void assertNoApiEvents() {
    try {
      apiSubscriber.await(300, TimeUnit.MILLISECONDS);
      apiSubscriber.assertNoValues();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private void assertApiEvent(ApiResponse event) {
    apiSubscriber.awaitCount(1);
    apiSubscriber.assertValue(event);
  }

  @Test @DisplayName("not authenticated")
  void notAuth() {
    client
      .when(request().withMethod(GET).withPath("/v1/quiz").withHeaders(header("p", "notauth")))
      .respond(response().withStatusCode(401));
    emulLoginAs(new OutPerson("noname", "notauth"));
    sut.out().accept(ApiRequest.GET_LIST);
    assertUiEvent(RootUIMessage.ACCESS_DENIED);
    assertNoApiEvents();
  }

  @Test @DisplayName("not logged in")
  void notLoggedIn() {
    sut.out().accept(ApiRequest.GET_LIST);
    assertUiEvent(RootUIMessage.NOT_LOGGED_IN);
    assertNoApiEvents();
  }

  @Test @DisplayName("processing error")
  void processingError() throws Exception {
    client.when(request().withMethod(GET).withPath("/v1/quiz").withHeaders(header("p", "author1")))
      .respond(response().withStatusCode(200).withBody("{"));
    emulLoginAs(TestData.author1);
    sut.out().accept(ApiRequest.GET_LIST);
    assertNoApiEvents();
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
    assertApiEvent(new ApiResponse.QuizList(TestData.list));
  }

  @Test @DisplayName("get full quiz")
  void getQuiz() {
    client
      .when(request().withMethod(GET).withPath("/v1/quiz/q1").withHeaders(header("p", "author1")))
      .respond(response().withStatusCode(200).withBody(toJson(TestData.fullQuiz1)));
    emulLoginAs(TestData.author1);
    sut.out().accept(new ApiRequest.GetQuiz("q1"));
    assertApiEvent(new ApiResponse.FullQuiz(TestData.fullQuiz1));
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
    assertApiEvent(new ApiResponse.QuizAdded(new OutQuizListed("q55", "new quiz title",
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
    assertApiEvent(new ApiResponse.PersonList(staff));
  }

  @Test @DisplayName("update quiz")
  void updateQuiz() {
    client
      .when(request().withMethod(PUT).withPath("/v1/quiz/q1").withHeaders(header("p", "author1"))
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(toJson(new InUpdateQuiz("new title", "new intnro", 99))))
      .respond(response().withStatusCode(204));
    emulLoginAs(TestData.author1);
    sut.out().accept(new ApiRequest.UpdateQuiz("q1", "new title", "new intro", 99));
    assertNoUiEvents();
    assertNoApiEvents();
  }

  @Test @DisplayName("set obsolete")
  void setObsolete() {
    client
      .when(request().withMethod(DELETE).withPath("/v1/quiz/q1").withHeaders(header("p", "curator")))
      .respond(response().withStatusCode(204));
    emulLoginAs(TestData.curator);
    sut.out().accept(new ApiRequest.SetObsolete("q1"));
    assertApiEvent(new ApiResponse.GotObsolete("q1"));
  }

  @Test @DisplayName("api error")
  void apiError() {
    val error = new OutError(new OutErrorReason(1122, "some error"), List.of("clue1", "clue2"));
    client
      .when(request().withMethod(DELETE).withPath("/v1/quiz/q1").withHeaders(header("p", "curator")))
      .respond(response().withStatusCode(422).withBody(toJson(error)));
    emulLoginAs(TestData.curator);
    sut.out().accept(new ApiRequest.SetObsolete("q1"));
    assertNoApiEvents();
    assertUiEvent(new RootUIMessage.ApiError(error));
  }

  @Test @DisplayName("add author")
  void addAuthor() {
    client
      .when(request().withMethod(PATCH).withPath("/v1/quiz/q1/authors/author3")
        .withHeaders(header("p", "curator")))
      .respond(response().withStatusCode(204));
    emulLoginAs(TestData.curator);
    sut.out().accept(new ApiRequest.AddAuthor("q1", "author3"));
    assertNoUiEvents();
    assertApiEvent(new ApiResponse.AuthorAdded("q1", "author3"));
  }

  @Test @DisplayName("remove author")
  void removeAuthor() {
    client
      .when(request().withMethod(DELETE).withPath("/v1/quiz/q3/authors/author1")
        .withHeaders(header("p", "curator")))
      .respond(response().withStatusCode(204));
    emulLoginAs(TestData.curator);
    sut.out().accept(new ApiRequest.RemoveAuthor("q3", "author1"));
    assertNoUiEvents();
    assertApiEvent(new ApiResponse.AuthorRemoved("q3", "author1"));
  }

  @Test @DisplayName("add inspector")
  void addInspector() {
    client
      .when(request().withMethod(PATCH).withPath("/v1/quiz/q1/inspectors/inspector3")
        .withHeaders(header("p", "curator")))
      .respond(response().withStatusCode(204));
    emulLoginAs(TestData.curator);
    sut.out().accept(new ApiRequest.AddInspector("q1", "inspector3"));
    assertNoUiEvents();
    assertApiEvent(new ApiResponse.InspectorAdded("q1", "inspector3"));
  }

  @Test @DisplayName("remove inspector")
  void removeInspector() {
    client.when(request().withMethod(DELETE).withPath("/v1/quiz/q3/inspectors/inspector1")
      .withHeaders(header("p", "curator")))
      .respond(response().withStatusCode(204));
    emulLoginAs(TestData.curator);
    sut.out().accept(new ApiRequest.RemoveInspector("q3", "inspector1"));
    assertNoUiEvents();
    assertApiEvent(new ApiResponse.InspectorRemoved("q3", "inspector1"));
  }

  @Test @DisplayName("create section")
  void createSection() {
    client.when(request().withMethod(POST).withPath("/v1/quiz/q1").withHeaders(header("p", "author1"))
      .withContentType(MediaType.APPLICATION_JSON).withBody(toJson(new InCreateSection("new section"))))
      .respond(response().withStatusCode(200).withBody("\"q1-1\""));
    emulLoginAs(TestData.author1);
    sut.out().accept(new ApiRequest.CreateSection("q1", "new section"));
    assertNoUiEvents();
    assertApiEvent(new ApiResponse.SectionCreated("q1", "q1-1"));
  }

  private static Stream<Arguments> sectionMove_args() {
    return Stream.of(
      Arguments.of("down", "q1", "q1-2", List.of("q1-1", "q1-2", "q1-3")),
      Arguments.of("up", "q1", "q1-1", List.of("q1-2", "q1-1", "q1-3"))
    );
  }

  @ParameterizedTest(name = "{0}") @DisplayName("move section")
  @MethodSource("sectionMove_args")
  void moveSection(String dir, String quizId, String sc, List<String> newOrder) {
    val up = "up".equals(dir);
    client
      .when(request().withMethod(PATCH).withPath("/v1/section/" + sc)
        .withQueryStringParameter("qid", quizId)
        .withQueryStringParameter("up", String.valueOf(up))
        .withHeaders(header("p", "author3")))
      .respond(response().withStatusCode(200).withBody(toJson(new OutStrList(newOrder))));
    emulLoginAs(TestData.author3);
    sut.out().accept(new ApiRequest.MoveSection(quizId, sc, up));
    assertNoUiEvents();
    assertApiEvent(new ApiResponse.SectionMoved(quizId, newOrder));
  }

  @Test @DisplayName("remove section")
  void removeSection() {
    client.when(request().withMethod(DELETE).withPath("/v1/section/q1-2")
      .withQueryStringParameter("qid", "q1").withHeaders(header("p", "author1")))
      .respond(response().withStatusCode(204));
    emulLoginAs(TestData.author1);
    sut.out().accept(new ApiRequest.RemoveSection("q1-2", "q1"));
    assertNoUiEvents();
    assertApiEvent(new ApiResponse.SectionRemoved("q1", "q1-2"));
  }
  
  private static Stream<Arguments> readinessAndApprovals_args() {
    return Stream.of(
      Arguments.of("on", "ready", TestData.author1, 
        new ApiRequest.SetReady("q1"), new ApiResponse.ReadySet("q1", "author1")),
      Arguments.of("off", "ready", TestData.author2,
        new ApiRequest.UnsetReady("q1"), new ApiResponse.ReadyUnset("q1", "author2")),
      Arguments.of("on", "resolve", TestData.inspector1,
        new ApiRequest.Approve("q1"), new ApiResponse.Approved("q1", "inspector1")),
      Arguments.of("off", "resolve", TestData.inspector2,
        new ApiRequest.Disapprove("q1"), new ApiResponse.Disapproved("q1", "inspector2"))
    );
  }

  @ParameterizedTest(name = "{1} {0}") @DisplayName("set/unset ready & approve/disapprove")
  @MethodSource("readinessAndApprovals_args")
  void readinessAndApprovals(String onOff, String concern, OutPerson user, ApiRequest req, ApiResponse res) {
    val method = "on".equals(onOff) ? PATCH : DELETE;
    client
      .when(request().withMethod(method).withPath("/v1/quiz/q1/" + concern)
        .withHeaders(header("p", user.id())))
      .respond(response().withStatusCode(204));
    emulLoginAs(user);
    sut.out().accept(req);
    assertNoUiEvents();
    assertApiEvent(res);
  }

  



  static String GET = "GET";
  static String POST = "POST";
  static String PUT = "PUT";
  static String PATCH = "PATCH";
  static String DELETE = "DELETE";

}

