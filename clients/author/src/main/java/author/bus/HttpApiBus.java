package author.bus;

import author.events.ApiResponse;
import author.requests.ApiRequest;
import author.events.LoginEvent;
import author.requests.LoginRequest;
import author.dtos.*;
import author.util.Bus;
import author.messages.RootUIMessage;

import static author.events.ApiResponse.*;
import static author.requests.ApiRequest.*;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

import java.util.function.*;
import java.util.concurrent.CompletableFuture;

import java.util.*;
import java.util.regex.*;
import java.io.InputStream;
import java.net.URI;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.introspect.DefaultAccessorNamingStrategy;

import lombok.val;

@lombok.extern.slf4j.Slf4j
public class HttpApiBus implements Bus<ApiResponse, ApiRequest> {

  private final ObjectMapper mapper = new ObjectMapper() {{
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    setAccessorNaming(new DefaultAccessorNamingStrategy.Provider().withGetterPrefix(""));
  }};

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
      .build();
  }


  @Override
  public Consumer<ApiRequest> out() {
    return r -> {
      if (user == null)
        errorOut.accept(RootUIMessage.NOT_LOGGED_IN);
      else
        process(r).thenAccept(resp -> {
          log.debug("finalize processing {}", resp);
          if (resp != NO_RESPONSE) {
            subject.onNext(resp);
            log.debug("published {}", resp);
          }
        });
    };
  }

  @Override
  public Observable<ApiResponse> in() {
    return subject;
  }

  private static final ApiResponse NO_RESPONSE = new ApiResponse() {
    @Override public String toString() { return "NO_RESPONSE"; }
  };

  private <T> T json(InputStream is, Class<T> clazz) {
    try {
      return mapper.readValue(is, clazz);
    } catch(Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private byte[] toJson(Object content) {
    try {
      return mapper.writeValueAsBytes(content);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private <T> List<T> jsonList(InputStream is, Class<T> clazz) {
    try {
      CollectionType ctype = mapper.getTypeFactory()
        .constructCollectionType(List.class, clazz);
      return mapper.readValue(is, ctype);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @SuppressWarnings("unchecked")
  private <Req extends ApiRequest> CompletableFuture<ApiResponse> process(Req request) {
    return calls.stream().filter(c -> c.matcher.test(request)).findAny()
      .map(call -> (Call<Req>) call)
      .map(call -> {
        return client.sendAsync(
            call.request.apply(request).header("p", user.id()).build(), 
            HttpResponse.BodyHandlers.ofInputStream()
          ).thenApply(resp -> {
            val fun = Optional.ofNullable(call.responses.get(resp.statusCode()))
              .map(f -> {
                log.debug("found resp mapper for {} {}", resp.statusCode(), request);
                return f;
              })
              .orElse((req, is) -> {
                switch (resp.statusCode()) {
                  case 401:
                    log.debug("send error on 401");
                    errorOut.accept(RootUIMessage.ACCESS_DENIED);
                    break;
                  case 422:
                    errorOut.accept(new RootUIMessage.ApiError(json(is, OutError.class)));
                    break;
                  default:
                    log.warn("unprocessed status code {} for {}", resp.statusCode(), request);
                }
                return NO_RESPONSE;
              });
            val result = fun.apply(request, resp.body());
            log.debug("mapped response to {}", result);
            //errorOut.accept(RootUIMessage.CLEAR);
            return result;
          })
          .exceptionally(ex -> {
            log.error("processing error", ex);
            errorOut.accept(new RootUIMessage.ProcessingError(ex));
            return NO_RESPONSE;
          });
      })
      .orElseGet(() -> {
        log.warn("call for {} not declared", request);
        return CompletableFuture.completedFuture(NO_RESPONSE);
      });
  }

  private static final Pattern pathPattern = Pattern.compile("\\{[^}]*}");

  private URI uri(String path, Object... segments) {
    val matcher = pathPattern.matcher(path);
    val sb = new StringBuilder();
    int i = 0;
    while (matcher.find())
      matcher.appendReplacement(sb, segments[i++].toString());
    matcher.appendTail(sb);
    return URI.create(baseUrl + sb.toString());
  }

  private HttpRequest.Builder reqBuilder(String path, Object... segments) {
    return HttpRequest.newBuilder().uri(uri(path, segments));
  }

  private List<Call<?>> calls = List.of(

    new Call<ApiRequest>(
      r -> r == GET_LIST,
      r -> reqBuilder("/quiz").GET(),
      Map.of(200, (r, is) -> new QuizList(jsonList(is, OutQuizListed.class)))
    ),

    new Call<GetQuiz>(
      r -> r instanceof GetQuiz,
      r -> reqBuilder("/quiz/{}", r.id()).GET(),
      Map.of(200, (r, is) -> new FullQuiz(json(is, OutFullQuiz.class)))
    ),

    new Call<Create>(
      r -> r instanceof Create,
      r -> reqBuilder("/quiz").header("content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofByteArray(toJson(new InCreateQuiz(
          r.id(), r.title(), "", 30, r.authors(), r.inspectors())))),
      Map.of(
        200, (r, is) -> {
          val details = json(is, OutCreateDetails.class);
          return new QuizAdded(new OutQuizListed(r.id(), r.title(), false,
            user, details.authors(), details.inspectors(), "Composing"));
        }
      )
    ),

    new Call<UpdateQuiz>(
      r -> r instanceof UpdateQuiz,
      r -> reqBuilder("/quiz/{}", r.quizId()).header("content-type", "application/json")
        .PUT(HttpRequest.BodyPublishers.ofByteArray(toJson(new InUpdateQuiz(
          r.title(), r.intro(), r.recommendedLength())))),
      Map.of(204, (r, is) -> NO_RESPONSE)
    ),

    new Call<ApiRequest>(
      r -> r == GET_STAFF,
      r -> reqBuilder("/staff").GET(),
      Map.of(200, (r, is) -> new PersonList(jsonList(is, OutPerson.class)))
    ),

    new Call<SetObsolete>(
      r -> r instanceof SetObsolete,
      r -> reqBuilder("/quiz/{}", r.quizId()).DELETE(),
      Map.of(204, (r, is) -> new GotObsolete(r.quizId()))
    ),

    new Call<AddAuthor>(
      r -> r instanceof AddAuthor,
      r -> reqBuilder("/quiz/{}/authors/{}", r.quizId(), r.personId())
        .method("PATCH", HttpRequest.BodyPublishers.noBody()),
      Map.of(204, (r, is) -> new AuthorAdded(r.quizId(), r.personId()))
    ),

    new Call<RemoveAuthor>(
      r -> r instanceof RemoveAuthor,
      r -> reqBuilder("/quiz/{}/authors/{}", r.quizId(), r.personId()).DELETE(),
      Map.of(204, (r, is) -> new AuthorRemoved(r.quizId(), r.personId()))
    ),

    new Call<AddInspector>(
      r -> r instanceof AddInspector,
      r -> reqBuilder("/quiz/{}/inspectors/{}", r.quizId(), r.personId())
        .method("PATCH", HttpRequest.BodyPublishers.noBody()),
      Map.of(204, (r, is) -> new InspectorAdded(r.quizId(), r.personId()))
    ),

    new Call<RemoveInspector>(
      r -> r instanceof RemoveInspector,
      r -> reqBuilder("/quiz/{}/inspectors/{}", r.quizId(), r.personId()).DELETE(),
      Map.of(204, (r, is) -> new InspectorRemoved(r.quizId(), r.personId()))
    ),

    new Call<CreateSection>(
      r -> r instanceof CreateSection,
      r -> reqBuilder("/quiz/{}", r.quizId())
        .header("content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofByteArray(toJson(new InCreateSection(r.title())))),
      Map.of(200, (r, is) -> new SectionCreated(r.quizId(), json(is, String.class)))
    ),

    new Call<MoveSection>(
      r -> r instanceof MoveSection,
      r -> reqBuilder("/section/{}?qid={}&up={}", r.sc(), r.quizId(), String.valueOf(r.up()))
        .method("PATCH", HttpRequest.BodyPublishers.noBody()),
      Map.of(200, (r, is) -> new SectionMoved(r.quizId(), json(is, OutStrList.class).list()))
    ),

    new Call<RemoveSection>(
      r -> r instanceof RemoveSection,
      r -> reqBuilder("/section/{}?qid={}", r.sc(), r.quizId()).DELETE(),
      Map.of(204, (r, is) -> new SectionRemoved(r.quizId(), r.sc()))
    ),

    new Call<SetReady>(
      r -> r instanceof SetReady,
      r -> reqBuilder("/quiz/{}/ready", r.quizId())
        .method("PATCH", HttpRequest.BodyPublishers.noBody()),
      Map.of(204, (r, is) -> new ReadySet(r.quizId(), user.id()))
    ),

    new Call<UnsetReady>(
      r -> r instanceof UnsetReady,
      r -> reqBuilder("/quiz/{}/ready", r.quizId()).DELETE(),
      Map.of(204, (r, is) -> new ReadyUnset(r.quizId(), user.id()))
    ),

    new Call<Approve>(
      r -> r instanceof Approve,
      r -> reqBuilder("/quiz/{}/resolve", r.quizId())
        .method("PATCH", HttpRequest.BodyPublishers.noBody()),
      Map.of(204, (r, is) -> new Approved(r.quizId(), user.id()))
    ),

    new Call<Disapprove>(
      r -> r instanceof Disapprove,
      r -> reqBuilder("/quiz/{}/resolve", r.quizId()).DELETE(),
      Map.of(204, (r, is) -> new Disapproved(r.quizId(), user.id()))
    ),

    new Call<UpdateSection>(
      r -> r instanceof UpdateSection,
      r -> reqBuilder("/section/{}", r.sectionSC())
        .header("content-type", "application/json")
        .PUT(HttpRequest.BodyPublishers.ofByteArray(toJson(new InUpdateSection(r.title(), r.intro())))),
      Map.of(204, (r, is) -> NO_RESPONSE)
    ),

    new Call<OwnSection>(
      r -> r instanceof OwnSection,
      r -> reqBuilder("/quiz/{}?sc={}", r.quizId(), r.sc())
        .method("PATCH", HttpRequest.BodyPublishers.noBody()),
      Map.of(204, (r, is) -> new SectionOwned(r.quizId(), r.sc()))
    ),

    new Call<DischargeSection>(
      r -> r instanceof DischargeSection,
      r -> reqBuilder("/section/{}", r.sc()).GET(),
      Map.of(204, (r, is) -> new SectionDischarged(r.sc()))
    ),

    new Call<AddItem>(
      r -> r instanceof AddItem,
      r -> reqBuilder("/section/{}/items", r.sectionSC())
        .method("PATCH", HttpRequest.BodyPublishers.noBody()),
      Map.of(200, (r, is) -> new ItemAdded(r.sectionSC(), json(is, String.class)))
    ),

    new Call<RemoveItem>(
      r -> r instanceof RemoveItem,
      r -> reqBuilder("/section/{}/items/{}", r.sectionSC(), r.sc()).DELETE(),
      Map.of(204, (r, is) -> new ItemRemoved(r.sectionSC(), r.sc()))
    ),

    new Call<MoveItem>(
      r -> r instanceof MoveItem,
      r -> reqBuilder("/section/{}/items/{}?up={}", r.sectionSC(), r.sc(), String.valueOf(r.up()))
        .method("PATCH", HttpRequest.BodyPublishers.noBody()),
      Map.of(200, (r, is) -> new ItemMoved(r.sectionSC(), json(is, OutStrList.class).list()))
    )

  );
  

  @lombok.AllArgsConstructor
  private static class Call<Req extends ApiRequest> {
    private Predicate<ApiRequest> matcher;
    private Function<Req, HttpRequest.Builder> request;
    private Map<Integer, BiFunction<Req, InputStream, ApiResponse>> responses;
  }

}

