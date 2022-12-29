package author.util;

import author.dtos.*;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.io.PrintWriter;
import java.io.IOException;

import java.util.function.Function;
import java.util.regex.*;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Collections;

import lombok.val;

public class Views {

  public static void html(OutFullQuiz quiz, PrintWriter writer) throws IOException {
    val parser = Parser.builder().build();
    val renderer = HtmlRenderer.builder().build();
    Function<String, String> md = in -> {
      val doc = parser.parse(in);
      return renderer.render(doc);
    };
    writer.println("<!DOCTYPE html>");
    writer.append("<html><head><title>").append(quiz.id()).append("</title>");
    writer.println("<style>");
    writer.println("body{padding:20px}");
    writer.println(".gray{color:#ddd}");
    writer.println("ul{list-style-type:none}");
    writer.println("input[type='text']{border-style:solid;border-width:0 0 1px 0;border-color:blue}");
    writer.println("hr.dotted{border:none;border-top:1px dashed #bbb}");
    writer.println("hr.solid{border:none;border-top:1px solid #bbb}");
    writer.println("iframe{border:none;width:100%;height:1.2em;padding:0");
    writer.println(".correct{color:green}");
    writer.println(".incorrect{color:red}");
    writer.println("</style>");
    writer.println("<script>");
    writer.println("function resize(f) {");
    writer.println("f.style.height = f.contentWindow.docuemtn.documentElement.scrollHeight + 'px'");
    writer.println("}");
    writer.println("</script>");
    writer.println("</head>");
    writer.println("<body>");

    writer.println("<h1>" + quiz.id() + "</h1>");
    writer.println("<h2>" + quiz.title() + "</h2>");
    writer.println(md.apply(quiz.intro()));

    for (val section : quiz.sections()) {
      writer.println("<section>");
      writer.println("<hr class=\"solid\"/>");
      writer.println("<h3>" + section.title() + "</h3>");
      writer.println(md.apply(section.intro()));

      for (val item : section.items()) {
        val ifname = String.format("res%s-%s-%s", quiz.id(), section.sc(), item.sc());
        writer.println("<form class=\"item\" action=\"/check\" method=\"post\"" +
          " enctype=\"application/x-www-form-urlencoded\" target=\"" + ifname + "\">");
        writer.println("<hr class=\"dotted\"/>");
        writer.println(md.apply(item.intro()));
        val itemRenderer = ItemRenderer.of(new IRP(writer, item, md, quiz.id(), section.sc()));
        itemRenderer.writeDefinition();
        itemRenderer.writeHints();

        writer.println("<input type=\"hidden\" name=\"qid\" value=\"" + quiz.id() + "\" />");
        writer.println("<input type=\"hidden\" name=\"ssc\" value=\"" + section.sc() + "\" />");
        writer.println("<input type=\"hidden\" name=\"isc\" value=\"" + item.sc() + "\" />");
        writer.println("<button type=\"submit\">Check</button>");
        writer.println("</form>");
        writer.println("<iframe name=\"" + ifname + "\" frameborder=\"0\" " +
          "scrolling=\"no\" onload=\"resize(this)\"></iframe>");
      }

      writer.println("</section>");
    }
    
    writer.println("</body>");
    writer.println("</html>");
    writer.flush();
  }

  private static abstract class SingleMulti extends ItemRenderer {
    private SingleMulti(IRP p) {
      super(p);
    }
    @Override final void writeDefinition() {
      writer.println(md.apply(item.definition().text()));
    }
    protected abstract String inputType();
    @Override final void writeHints() {
      writer.println("<ul class=\"sol\">");

      for (int i = 0; i < item.hints().size(); i++) {
        val id = String.format("%s-%s-%s-%s", quizId, sectionSc, item.sc(), i);
        writer.println("<li>");
        writer.println("<input type=\"" + inputType() + "\" id=\"" + id + "\" value=\"" + 
          i + "\" name=\"sol\" />");
        writer.println("<label for=\"" + id + "\">" + item.hints().get(i).get(0).text() + "</label>");
        writer.println("</li>");
      }

      writer.println("</ul>");
    }

  }

  private static class SingleChoice extends SingleMulti {
    private SingleChoice(IRP p) {
      super(p);
    }
    @Override protected String inputType() { return "radio"; }
  }

  private static class MultiChoice extends SingleMulti {
    private MultiChoice(IRP p) {
      super(p);
    }
    @Override protected String inputType() { return "checkbox"; }
  }

  private static abstract class Fill extends ItemRenderer {
    private Fill(IRP p) {
      super(p);
    }
    protected abstract String input();
    @Override final void writeDefinition() {
      val matcher = PH_PATTERN.matcher(md.apply(item.definition().text()));
      val sb = new StringBuilder();
      while (matcher.find()) {
        val idx = Integer.parseInt(matcher.group(0).replace("{{", "").replace("}}", ""));
        val value = item.hints().get(idx - 1).get(0).text();
        matcher.appendReplacement(sb, input() + 
              "<span class=\"gray\">(" + value + ")</span>");
      }
      matcher.appendTail(sb);
      writer.println(sb.toString());
    }
    @Override final void writeHints() { }
  }

  private static class FillSelect extends Fill {
    private FillSelect(IRP p) {
      super(p);
    }
    @Override protected String input() {
      String input = "<select name=\"sol\">";
      int c = 0;
      for (val hint : item.hints())
        input += "<option value=\"" + (c++) + "\">" + hint.get(0).text() + "</option>";
      input += "</select>";
      return input;
    }
  }

  private static class FillEnter extends Fill {
    private FillEnter(IRP p) {
      super(p);
    }
    @Override protected String input() {
      return "<input type=\"text\" name=\"answer\"/>";
    }
  }

  @lombok.AllArgsConstructor
  private static class IRP {
    PrintWriter writer;
    OutItem item;
    Function<String, String> md;
    String quizId;
    String sectionSc;
  }

  private static final Pattern PH_PATTERN = Pattern.compile("\\{\\{\\d+}}");
  
  private static abstract class ItemRenderer {
    protected PrintWriter writer;
    protected OutItem item;
    protected Function<String, String> md;
    protected String quizId;
    protected String sectionSc;
    private ItemRenderer(IRP p) {
      writer = p.writer;
      item = p.item;
      md = p.md;
      quizId = p.quizId;
      sectionSc = p.sectionSc;
    }
    abstract void writeDefinition();
    abstract void writeHints();
    static ItemRenderer of(IRP p) {
      if (p.item.definition().text().indexOf("{{") > -1) {
        if (p.item.hintsVisible())
          return new FillSelect(p);
        else
          return new FillEnter(p);
      } else {
        if (p.item.solutions().size() > 1)
          return new MultiChoice(p);
        else
          return new SingleChoice(p);
      }
    }
  }

  @lombok.Value
  public static class Answer {
    String text;
    boolean correct;
    public static Answer correct(String text) {
      return new Answer(text, true);
    }
    public static Answer incorrect(String text) {
      return new Answer(text, false);
    }
  }

  @lombok.Value
  public static class CheckResult {
    List<List<String>> expected;
    List<Answer> answers;
    public boolean isCorrect() {
      return answers.stream().allMatch(Answer::correct);
    }
  }

  public static CheckResult checkIndexed(OutFullQuiz quiz, String sectionSc, String itemSc, 
      List<Integer> solution) {
    val section = quiz.sections().stream().filter(s -> s.sc().equals(sectionSc))
      .findAny().orElseThrow(() -> new IllegalArgumentException("Section '" + sectionSc + "' not found"));
    val item = section.items().stream().filter(i -> i.sc().equals(itemSc))
      .findAny().orElseThrow(() -> new IllegalArgumentException("Item '" + itemSc + "' not found"));
    val correctHints = new ArrayList<List<OutStatement>>();
    val matcher = PH_PATTERN.matcher(item.definition().text());
    List<Integer> realSolutions = new ArrayList<Integer>();
    while (matcher.find())
      realSolutions.add(Integer.parseInt(matcher.group(0).replace("{{", "").replace("}}", "")) - 1);

    System.out.println("extracted sols: " + realSolutions);
    if (realSolutions.isEmpty())
      realSolutions = item.solutions();
    //for (int i = 0; i < item.hints().size(); i++)
    //  if (realSolutions.contains(i))
    //    correctHints.add(item.hints().get(i));
    //val expected = correctHints.stream().map(hs -> List.of(hs.get(0).text()))
    //  .collect(Collectors.toList());
    val expected = realSolutions.stream().map(i -> List.of(item.hints().get(i).get(0).text()))
      .collect(Collectors.toList());
    val answers = new ArrayList<Answer>();
    for (val sol : solution)
      if (realSolutions.contains(sol)) 
        answers.add(Answer.correct(item.hints().get(sol).get(0).text()));
      else 
        answers.add(Answer.incorrect(item.hints().size() > sol ?
                                              item.hints().get(sol).get(0).text() : 
                                              "???"));
    return new CheckResult(expected, Collections.unmodifiableList(answers));
  }

  public static CheckResult checkHandwritten(OutFullQuiz quiz, String sectionSc, String itemSc,
      List<String> answers) {
    return null;
  }

}
