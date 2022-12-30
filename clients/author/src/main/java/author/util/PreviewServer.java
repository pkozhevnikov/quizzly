package author.util;

import author.events.*;
import author.requests.*;
import author.dtos.*;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.util.*;
import java.util.stream.*;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

import java.nio.charset.StandardCharsets;

import lombok.val;

public class PreviewServer {

  private HttpServer server;
  
  private static final Map<String, OutFullQuiz> quizzes = new ConcurrentHashMap<>();

  public PreviewServer(Bus<ApiResponse, ApiRequest> apiBus) {
    apiBus.in().ofType(ApiResponse.FullQuiz.class)
      .subscribe(e -> quizzes.put(e.quiz().id(), e.quiz()));
  }

  public void start(int port) {
    try {
    server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/preview", this::preview);
    server.createContext("/check", this::check);
    server.start();
    } catch (Exception ex) {
      throw new RuntimeException("could not start preview server", ex);
    }
  }

  public static int freePort() {
    int previewPort = 10123;
    try (val socket = new ServerSocket(0)) {
      previewPort = socket.getLocalPort();
    } catch (Exception ignore) {}
    return previewPort;
  }

  public void stop() {
    if (server != null)
      server.stop(1);
  }

  private static final Pattern P = Pattern.compile("([^=]+)=(.*)");

  private Map<String, List<String>> getParams(String query) {
    if (query == null) 
      return Collections.emptyMap();
    val result = new HashMap<String, List<String>>();
    val m1 = query.split("&");
    for (val pair : m1) {
      val m2 = P.matcher(pair);
      while (m2.find()) {
        val key = m2.group(1);
        val value = URLDecoder.decode(m2.group(2), StandardCharsets.UTF_8);
        result.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
      }
    }
    return result;
  }

  private void preview(HttpExchange exchange) throws IOException {
    val query = exchange.getRequestURI().getQuery();
    val params = getParams(query);
    val ous = exchange.getResponseBody();
    if (!params.containsKey("qid"))
      exchange.sendResponseHeaders(400, 0);
    else {
      val quiz = quizzes.get(params.get("qid").get(0));
      if (quiz == null)
        exchange.sendResponseHeaders(410, 0);
      else {
        exchange.sendResponseHeaders(200, 0);
        Views.html(quiz, new PrintWriter(ous));
      }
    }
    ous.close();
  }

  private void check(HttpExchange exchange) throws IOException {
    val in = exchange.getRequestBody();
    val query = new String(in.readAllBytes());
    in.close();
    val params = getParams(query);
    val ous = exchange.getResponseBody();
    if (!params.containsKey("qid") || !params.containsKey("ssc") ||
        !params.containsKey("isc") || (!params.containsKey("sol") && !params.containsKey("answer")))
      exchange.sendResponseHeaders(400, 0);
    else {
      val quiz = quizzes.get(params.get("qid").get(0));
      if (quiz == null)
        exchange.sendResponseHeaders(410, 0);
      else {
        val ssc = params.get("ssc").get(0);
        val isc = params.get("isc").get(0);
        val item = quiz.sections().stream().filter(s -> s.sc().equals(ssc)).findAny()
          .flatMap(s -> s.items().stream().filter(i -> i.sc().equals(isc)).findAny())
          .orElse(null);
        if (item == null)
          exchange.sendResponseHeaders(410, 0);
        else {
          exchange.sendResponseHeaders(200, 0);
          if (item.definition().text().indexOf("{{") > -1 && !item.hintsVisible()) {
            render(Views.checkHandwritten(quiz, ssc, isc, params.get("answer")), new PrintWriter(ous));
          } else {
            val sol = params.get("sol").stream().map(Integer::valueOf).collect(Collectors.toList());
            render(Views.checkIndexed(quiz, ssc, isc, sol), new PrintWriter(ous));
          }
        }
      }
    }
    ous.close();
  }

  private void render(Views.CheckResult result, PrintWriter writer) {
    writer.println("<!DOCTYPE html>");
    writer.println("<html>");
    writer.println("<head>");
    writer.println("<style>");
    writer.println(".correct{color:green}");
    writer.println(".incorrect{color:red}");
    writer.println(".neutral{color:blue}");
    writer.println("td>div:nth-child(n+1){padding-left:10px}");
    writer.println("</style>");
    writer.println("<body>");
    writer.println("<table border=\"0\">");
    for (int i = 0; i < result.expected().size(); i++) {
      writer.println("<tr>");
      writer.println("<td>");
      for (val exp : result.expected().get(i))
        writer.println("<div class=\"neutral\">" + exp + "</div>");
      writer.println("</td>");
      writer.println("<td>");
      val ans = result.answers().get(i);
      writer.println("<div class=\"" + (ans.correct() ? "correct" : "incorrect") +
        "\">" + ans.text() + "</div>");
      writer.println("</td>");
      writer.println("</tr>");
    }
    writer.println("</table>");
    writer.println("</body>");
    writer.println("</html>");
    writer.flush();
  }

}
