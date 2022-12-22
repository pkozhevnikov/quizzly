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

  private CompletableFuture<ApiResponse> process(ApiRequest request) {
    val call = forRequest(request);
    if (call == null) {
      log.warn("call for {} not declared", request);
      return CompletableFuture.completedFuture(NO_RESPONSE);
    }
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
        return result;
      })
      .exceptionally(ex -> {
        log.error("processing error", ex);
        errorOut.accept(new RootUIMessage.ProcessingError(ex));
        return NO_RESPONSE;
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

  @SuppressWarnings("unchecked")
  private <Req extends ApiRequest> Call<Req> forRequest(ApiRequest req) {
    if (req == GET_LIST) 
      return (Call<Req>) new Call<ApiRequest>(
        r -> reqBuilder("/quiz").GET(),
        Map.of(
          200, (r, is) -> new QuizList(jsonList(is, OutQuizListed.class))
        )
      );
    else if (req instanceof GetQuiz) 
      return (Call<Req>) new Call<GetQuiz>(
        r -> reqBuilder("/quiz/{}", r.id()).GET(),
        Map.of(
          200, (r, is) -> new FullQuiz(json(is, OutFullQuiz.class))
        )
      );
    else if (req instanceof Create)
      return (Call<Req>) new Call<Create>(
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
      );
    else if (req == GET_STAFF)
      return (Call<Req>) new Call<ApiRequest>(
        r -> reqBuilder("/staff").GET(),
        Map.of(
          200, (r, is) -> new PersonList(jsonList(is, OutPerson.class))
        )
      );
      
    return null;
  }

  @lombok.AllArgsConstructor
  private static class Call<Req extends ApiRequest> {
    private Function<Req, HttpRequest.Builder> request;
    private Map<Integer, BiFunction<Req, InputStream, ApiResponse>> responses;
  }

}

