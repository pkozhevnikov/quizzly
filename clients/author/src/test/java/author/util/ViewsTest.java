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
import static author.testutil.ElementAssert.*;

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

  @Test @DisplayName("item forms have correct common fields")
  void htmlEntireItemForm() throws Exception {
    val sw = new StringWriter();
    val pw = new PrintWriter(sw);
    val quiz = quiz("spc", "spc title", "spc **intro**", List.of(
      new OutSection("s1", "s1 title", "s1 **intro**", List.of(singleChoice))
    ));
    Views.html(quiz, pw);
    val doc = Jsoup.parse(sw.toString());
    val form = doc.selectFirst("form.item");
    assertThat(form).isNotNull();
    assertThat(form.attributes().get("action")).isEqualTo("/check");
    assertThat(form.attributes().get("enctype")).isEqualTo("application/x-www-form-urlencoded");
    assertThat(form.attributes().get("method")).isEqualTo("post");
    assertThat(form.select("input[type=hidden]")).satisfiesExactlyInAnyOrder(
      h -> {
        assertThat(h.attributes().get("name")).isEqualTo("qid");
        assertThat(h.attributes().get("value")).isEqualTo("spc");
      },
      h -> {
        assertThat(h.attributes().get("name")).isEqualTo("ssc");
        assertThat(h.attributes().get("value")).isEqualTo("s1");
      },
      h -> {
        assertThat(h.attributes().get("name")).isEqualTo("isc");
        assertThat(h.attributes().get("value")).isEqualTo("sinchoice");
      }
    );
    assertThat(form.select("button[type=submit]")).isNotNull();
  }

  @Test @DisplayName("single choice item is rendered correctly")
  void htmlSingleChoice() throws Exception {
    val sw = new StringWriter();
    val pw = new PrintWriter(sw);
    val quiz = quiz("sch", "sch", "sch intro", List.of(
      new OutSection("s1", "s1 title", "s1 intro", List.of(singleChoice))
    ));
    Views.html(quiz, pw);
    val doc = Jsoup.parse(sw.toString());
    val form = doc.selectFirst("form.item");
    assertThat(form).isNotNull();
    assertThat(form.child(1)).hasHtml("sinchoice <strong>intro</strong>");
    assertThat(form.child(2)).hasHtml("sinchoice <strong>definition</strong>");
    val sol = form.selectFirst("ul.sol");
    assertThat(sol).isNotNull();
    assertThat(sol.select("input[type=radio]").size()).isEqualTo(3);
    assertThat(sol.select("input[type=radio]")).satisfiesExactly(
      r -> {
        val id = "sch-s1-sinchoice-0";
        assertThat(r)
          .hasAttr("name", "sol")
          .hasVal("0")
          .hasId(id);
        assertThat(r.nextElementSibling())
          .is("label")
          .hasAttr("for", id)
          .hasText("sinchoice hint 1");
      },
      r -> {
        val id = "sch-s1-sinchoice-1";
        assertThat(r)
          .hasAttr("name", "sol")
          .hasVal("1")
          .hasId(id);
        assertThat(r.nextElementSibling())
          .is("label")
          .hasAttr("for", id)
          .hasText("sinchoice hint 2");
      },
      r -> {
        val id = "sch-s1-sinchoice-2";
        assertThat(r)
          .hasAttr("name", "sol")
          .hasVal("2")
          .hasId(id);
        assertThat(r.nextElementSibling())
          .is("label")
          .hasAttr("for", id)
          .hasText("sinchoice hint 3");
      }
    );
  }

  @Test @DisplayName("multi choice item is rendered currectly")
  void htmlMultiChoice() throws Exception {
    val sw = new StringWriter();
    val pw = new PrintWriter(sw);
    val quiz = quiz("mch", "mch", "mch intro", List.of(
      new OutSection("s1", "s1 title", "s1 intro", List.of(multiChoice))
    ));
    Views.html(quiz, pw);
    val doc = Jsoup.parse(sw.toString());
    val form = doc.selectFirst("form.item");
    assertThat(form).isNotNull();
    assertThat(form.child(1)).hasHtml("mulchoice <strong>intro</strong>");
    assertThat(form.child(2)).hasHtml("mulchoice <strong>definition</strong>");
    val sol = form.selectFirst("ul.sol");
    assertThat(sol).isNotNull();
    assertThat(sol.select("input[type=checkbox]").size()).isEqualTo(3);
    assertThat(sol.select("input[type=checkbox]")).satisfiesExactly(
      c -> {
        val id = "mch-s1-mulchoice-0";
        assertThat(c)
          .hasAttr("name", "sol")
          .hasVal("0")
          .hasId(id);
        assertThat(c.nextElementSibling())
          .is("label")
          .hasAttr("for", id)
          .hasText("mulchoice hint 1");
      },
      c -> {
        val id = "mch-s1-mulchoice-1";
        assertThat(c)
          .hasAttr("name", "sol")
          .hasVal("1")
          .hasId(id);
        assertThat(c.nextElementSibling())
          .is("label")
          .hasAttr("for", id)
          .hasText("mulchoice hint 2");
      },
      c -> {
        val id = "mch-s1-mulchoice-2";
        assertThat(c)
          .hasAttr("name", "sol")
          .hasVal("2")
          .hasId(id);
        assertThat(c.nextElementSibling())
          .is("label")
          .hasAttr("for", id)
          .hasText("mulchoice hint 3");
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
