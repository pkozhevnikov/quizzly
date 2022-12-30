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
    assertThat(doc.selectFirst("h1")).hasText("ent");
    assertThat(doc.selectFirst("h2")).hasText("ent title");
    assertThat(doc.selectFirst("h2").nextElementSibling())
      .hasText("ent intro")
      .hasHtml("ent <strong>intro</strong>");
    assertThat(doc.select("section")).satisfiesExactly(
      s -> {
        assertThat(s.child(0)).is("hr.solid");
        assertThat(s.child(1)).is("h3").hasText("sec1 title");
        assertThat(s.child(2)).is("p").hasHtml("sec1 <strong>intro</strong>");
        assertThat(s.select("form.item").size()).isEqualTo(1);
        val form = s.selectFirst("form.item");
        assertThat(form.child(0)).is("hr.dotted");
        assertThat(form.child(1)).is("p").hasHtml("sinchoice <strong>intro</strong>");
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
    assertThat(form)
      .isNotNull()
      .hasAttr("action", "/check")
      .hasAttr("enctype", "application/x-www-form-urlencoded")
      .hasAttr("method", "post")
      .hasAttr("target", "resspc-s1-sinchoice")
      ;
    assertThat(form.select("input[type=hidden]")).satisfiesExactlyInAnyOrder(
      h -> assertThat(h).hasAttr("name", "qid").hasVal("spc"),
      h -> assertThat(h).hasAttr("name", "ssc").hasVal("s1"),
      h -> assertThat(h).hasAttr("name", "isc").hasVal("sinchoice")
    );
    assertThat(form.select("button[type=submit]")).isNotNull();
    assertThat(form.nextElementSibling())
      .isNotNull()
      .is("iframe")
      .hasAttr("name", "resspc-s1-sinchoice");
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

  @Test @DisplayName("multi choice item is rendered correctly")
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

  @Test @DisplayName("fill by select item is rendered correctly")
  void htmlFillSelect() throws Exception {
    val sw = new StringWriter();
    val pw = new PrintWriter(sw);
    val quiz = quiz("fs", "fs", "fs intro", List.of(
      new OutSection("s1", "s1 title", "s1 intro", List.of(fillSelect))
    ));
    Views.html(quiz, pw);
    val doc = Jsoup.parse(sw.toString());
    val form = doc.selectFirst("form.item");
    assertThat(form).isNotNull();
    assertThat(form.child(1)).hasHtml("fillsel <strong>intro</strong>");
    assertThat(form.selectFirst("ul.sol")).isNull();
    val def = form.child(2);
    assertThat(def.select("select").size()).isEqualTo(3);
    assertThat(def.text())
      .doesNotContain("{{1}}").doesNotContain("{{2}}").doesNotContain("{{3}}");
    assertThat(def.select("select")).allSatisfy(sel -> {
      assertThat(sel).hasAttr("name", "sol");
      assertThat(sel.select("option")).satisfiesExactly(
        o -> assertThat(o).hasVal("0").hasText("fillsel hint 1"),
        o -> assertThat(o).hasVal("1").hasText("fillsel hint 2"),
        o -> assertThat(o).hasVal("2").hasText("fillsel hint 3")
      );
    });
    assertThat(def.select("select")).satisfiesExactly(
      sel -> assertThat(sel.nextElementSibling()).is("span").hasText("(fillsel hint 2)"),
      sel -> assertThat(sel.nextElementSibling()).is("span").hasText("(fillsel hint 3)"),
      sel -> assertThat(sel.nextElementSibling()).is("span").hasText("(fillsel hint 1)")
    );
    assertThat(def.html()).startsWith("fillsel <strong>definition</strong>");
  }

  @Test @DisplayName("fill by hand item is rendered correctly")
  void htmlFillEnter() throws Exception {
    val sw = new StringWriter();
    val pw = new PrintWriter(sw);
    val quiz = quiz("fe", "fe", "fe intro", List.of(
      new OutSection("s1", "s1 title", "s1 intro", List.of(fillEnter))
    ));
    Views.html(quiz, pw);
    val doc = Jsoup.parse(sw.toString());
    val form = doc.selectFirst("form.item");
    assertThat(form).isNotNull();
    assertThat(form.child(1)).hasHtml("fillent <strong>intro</strong>");
    assertThat(form.selectFirst("ul.sol")).isNull();
    val def = form.child(2);
    assertThat(def.select("input[type=text]")).size().isEqualTo(3);
    assertThat(def.text())
      .doesNotContain("{{1}}").doesNotContain("{{2}}").doesNotContain("{{3}}");
    assertThat(def.select("input[type=text]")).allSatisfy(inp -> 
      assertThat(inp).hasAttr("name", "answer"));
    assertThat(def.select("input[type=text]")).satisfiesExactly(
      inp -> assertThat(inp.nextElementSibling()).is("span").hasText("(fillent hint 2)"),
      inp -> assertThat(inp.nextElementSibling()).is("span").hasText("(fillent hint 3)"),
      inp -> assertThat(inp.nextElementSibling()).is("span").hasText("(fillent hint 1)")
    );
    assertThat(def.html()).startsWith("fillent <strong>definition</strong>");
  }

  private static Stream<Arguments> checkIndexed_args() {
    return Stream.of(
      Arguments.of("single choice", singleChoice,
        List.of(1),
        new Views.CheckResult(
          List.of(List.of("sinchoice hint 2")), 
            List.of(Views.Answer.correct("sinchoice hint 2"))
        ),
        List.of(2),
        new Views.CheckResult(
          List.of(List.of("sinchoice hint 2")), 
            List.of(Views.Answer.incorrect("sinchoice hint 3"))
        )
      ),
      Arguments.of("multi choice", multiChoice,
        List.of(0, 2),
        new Views.CheckResult(
          List.of(List.of("mulchoice hint 1"), List.of("mulchoice hint 3")),
            List.of(Views.Answer.correct("mulchoice hint 1"), Views.Answer.correct("mulchoice hint 3"))
        ),
        List.of(1, 2),
        new Views.CheckResult(
          List.of(List.of("mulchoice hint 1"), List.of("mulchoice hint 3")),
            List.of(Views.Answer.incorrect("mulchoice hint 2"), Views.Answer.correct("mulchoice hint 3"))
        )
      ),
      Arguments.of("fill select", fillSelect,
        List.of(1, 2, 0),
        new Views.CheckResult(
          List.of(List.of("fillsel hint 2"), List.of("fillsel hint 3"), List.of("fillsel hint 1")),
            List.of(Views.Answer.correct("fillsel hint 2"), Views.Answer.correct("fillsel hint 3"),
              Views.Answer.correct("fillsel hint 1"))
        ),
        List.of(0, 2, 1),
        new Views.CheckResult(
          List.of(List.of("fillsel hint 2"), List.of("fillsel hint 3"), List.of("fillsel hint 1")),
            List.of(Views.Answer.incorrect("fillsel hint 1"), Views.Answer.correct("fillsel hint 3"),
              Views.Answer.incorrect("fillsel hint 2"))
        )
      )
    );
  }

  @ParameterizedTest(name = "{0}") @DisplayName("checks indexed solution")
  @MethodSource("checkIndexed_args")
  void checkIndexed(String name, OutItem item, 
      List<Integer> correctSol, Views.CheckResult correctRes, 
      List<Integer> incorrectSol, Views.CheckResult incorrectRes) {
    val quiz = quiz("sc1", "sc1", "sc1 intro", List.of(
      new OutSection("s1", "s1 title", "s1 intro", List.of(item))
    ));
    val res1 = Views.checkIndexed(quiz, "s1", item.sc(), correctSol);
    assertThat(res1.isCorrect()).isTrue();
    assertThat(res1).isEqualTo(correctRes);

    val res2 = Views.checkIndexed(quiz, "s1", item.sc(), incorrectSol);
    assertThat(res2.isCorrect()).isFalse();
    assertThat(res2).isEqualTo(incorrectRes);

    assertThatIllegalArgumentException()
      .isThrownBy(() -> { Views.checkIndexed(quiz, "ne", "xyz", correctSol); })
      .withMessage("Section 'ne' not found");
    assertThatIllegalArgumentException()
      .isThrownBy(() -> { Views.checkIndexed(quiz, "s1", "ne", correctSol); })
      .withMessage("Item 'ne' not found");
  }

  @Test @DisplayName("checks handwritten solution")
  void checkHandwritten() {
    val quiz = quiz("sc1", "sc1", "sc1 intro", List.of(
      new OutSection("s1", "s1 title", "s1 intro", List.of(fillEnter))
    ));
    val sol1 = List.of("fillent hint 2", "fillent Hint  3 ", "fillent hint 1 alt");
    val res1 = Views.checkHandwritten(quiz, "s1", "fillent", sol1);
    assertThat(res1.isCorrect()).isTrue();
    assertThat(res1).isEqualTo(new Views.CheckResult(
      List.of( List.of("fillent hint 2"), List.of("fillent hint 3"), 
          List.of("fillent hint 1", "fillent hint 1 alt")),
      List.of(
        Views.Answer.correct("fillent hint 2"),
        Views.Answer.correct("fillent Hint  3 "),
        Views.Answer.correct("fillent hint 1 alt")
      )
    ));

    val sol2 = List.of("fillent hint 2", "xyz", "XYZ");
    val res2 = Views.checkHandwritten(quiz, "s1", "fillent", sol2);
    assertThat(res2.isCorrect()).isFalse();
    assertThat(res2).isEqualTo(new Views.CheckResult(
      List.of(List.of("fillent hint 2"), List.of("fillent hint 3"),
          List.of("fillent hint 1", "fillent hint 1 alt")),
      List.of(
        Views.Answer.correct("fillent hint 2"),
        Views.Answer.incorrect("xyz"),
        Views.Answer.incorrect("XYZ")
      )
    ));

    assertThatIllegalArgumentException()
      .isThrownBy(() -> { Views.checkHandwritten(quiz, "ne", "xyz", List.of()); })
      .withMessage("Section 'ne' not found");
    assertThatIllegalArgumentException()
      .isThrownBy(() -> { Views.checkHandwritten(quiz, "s1", "ne", List.of()); })
      .withMessage("Item 'ne' not found");
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
    "fillsel **intro**",
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
