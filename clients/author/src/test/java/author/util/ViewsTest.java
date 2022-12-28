package author.bus;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.*;
import java.util.stream.*;
import java.util.function.*;
import java.util.concurrent.*;

import java.io.*;

import javafx.application.Platform;

import org.assertj.core.api.*;
import static org.assertj.core.api.Assertions.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

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

public class ViewsTest {

  @Test
  @Disabled
  void getHtml() throws Exception {
    val pw = new PrintWriter("out.html");
    val quiz = new OutFullQuiz(
      "T-1",
      "the title",
      "some **text** {{1}} (the / word)",
      null,
      null,
      null,
      10,
      null,
      null,
      null,
      false,
      List.of(
        new OutSection(
          "T-1-1",
          "section 1",
          "section intro",
          List.of(
            new OutItem(
              "1",
              "",
              new OutStatement("item *text* {{1}} {{2}}", null),
              List.of(
                List.of(new OutStatement("def 2", null)),
                List.of(new OutStatement("def 1", null))
              ),
              false,
              List.of(0)
            )
          )
        )
      ),
      "composing"
    );
    Views.htmlOf(quiz, pw);
    pw.flush();
    pw.close();
  }

  @Test @DisplayName("entire structure of html is correct")
  void htmlEntireStructure() throws Exception {
    val sw = new StringWriter();
    val pw = new PrintWriter(sw);
    val quiz = quiz("ent", "ent title", "ent **intro**", List.of(
      new OutSection("sec1", "sec1 title", "sec1 **intro**", List.of(singleChoice)),
      new OutSection("sec2", "sec2 title", "sec2 **intro**", List.of(multiChoice, fillSelect))
    ));
    Views.html(quiz, pw);
    val doc = Jsoup.parse(sw.toString());
    assertThat(doc.selectFirst("h1").text()).isEqualTo("ent");
    assertThat(doc.selectFirst("h2").text()).isEqualTo("ent title");
    assertThat(doc.selectFirst("h2").nextElementSibling().text()).isEqualTo("ent intro");
    assertThat(doc.selectFirst("h2").nextElementSibling().html()).isEqualTo("ent <strong>intro</strong>");
    assertThat(doc.select("section")).satisfiesExactly(
      s -> {
        assertThat(s.child(0).is("hr.solid")).isTrue();
        assertThat(s.child(1).is("h3")).isTrue();
        assertThat(s.child(1).text()).isEqualTo("sec1 title");
        assertThat(s.child(2).is("p")).isTrue();
        assertThat(s.child(2).html()).isEqualTo("sec1 <strong>intro</strong>");
        assertThat(s.select("form.item").size()).isEqualTo(1);
        val form = s.selectFirst("form.item");
        assertThat(form.child(0).is("hr.dotted")).isTrue();
        assertThat(form.child(1).is("p")).isTrue();
        assertThat(form.child(1).html()).isEqualTo("sinchoice <strong>intro</strong>");
      },
      s -> {
        assertThat(s.select("form.item").size()).isEqualTo(2);
      }
    );
  }

  private static OutItem singleChoice = new OutItem(
    "sinchoice",
    "sinchoice **intro**",
    new OutStatement("sinchoice **definition**", null),
    List.of(
      List.of(new OutStatement("sinchoice hint 1", null)),
      List.of(new OutStatement("sinchoice hint 2", null)),
      List.of(new OutStatement("sinchoice hint 3", null))
    ),
    true,
    List.of(1)
  );

  private static OutItem multiChoice = new OutItem(
    "mulchoice",
    "mulchoice **intro**",
    new OutStatement("mulchoice **definition**", null),
    List.of(
      List.of(new OutStatement("mulchoice hint 1", null)),
      List.of(new OutStatement("mulchoice hint 2", null)),
      List.of(new OutStatement("mulchoice hint 3", null))
    ),
    true,
    List.of(0, 2)
  );

  private static OutItem fillSelect = new OutItem(
    "fillsel",
    "fillsel **into**",
    new OutStatement("fillsel **definition** {{2}} {{3}} {{1}}", null),
    List.of(
      List.of(new OutStatement("fillsel hint 1", null)),
      List.of(new OutStatement("fillsel hint 2", null)),
      List.of(new OutStatement("fillsel hint 3", null))
    ),
    true,
    List.of()
  );

  private static OutItem fillEnter = new OutItem(
    "fillent",
    "fillent **intro**",
    new OutStatement("fillent **definition** {{2}} {{3}} {{1}}", null),
    List.of(
      List.of(
        new OutStatement("fillent hint 1", null),
        new OutStatement("fillent hint 1 alt", null)
      ),
      List.of(new OutStatement("fillent hint 2", null)),
      List.of(new OutStatement("fillent hint 3", null))
    ),
    false,
    List.of()
  );

  private static OutFullQuiz quiz(String id, String title, String intro, List<OutSection> sections) {
    return new OutFullQuiz(
      id,
      title,
      intro,
      null,
      null,
      null,
      0,
      null,
      null,
      null,
      false,
      sections,
      ""
    );
  }
    
    
}
