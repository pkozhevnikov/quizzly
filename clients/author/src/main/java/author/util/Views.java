package author.util;

import author.dtos.*;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.io.PrintWriter;
import java.io.IOException;

import java.util.function.Function;
import java.util.regex.*;

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
    writer.println("iframe{border:none}");
    writer.println(".correct{color:green}");
    writer.println(".incorrect{color:red}");
    writer.println("</style>");
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
        writer.println("<form class=\"item\" action=\"/check\" method=\"post\"" +
          " enctype=\"application/x-www-form-urlencoded\">");
        writer.println("<hr class=\"dotted\"/>");
        writer.println(md.apply(item.intro()));
        writer.println(md.apply(item.definition().text()));

        if (item.hintsVisible()) {
          writer.println("<ul class=\"sol\">");

          val type = item.solutions().size() > 1 ? "checkbox" : "radio";
          for (int i = 0; i < item.hints().size(); i++) {
            val id = String.format("%s-%s-%s-%s", quiz.id(), section.sc(), item.sc(), i);
            writer.println("<li>");
            writer.println("<input type=\"" + type + "\" id=\"" + id + "\" value=\"" + 
              i + "\" name=\"sol\" />");
            writer.println("<label for=\"" + id + "\">" + item.hints().get(i).get(0).text() + "</label>");
            writer.println("</li>");
          }

          writer.println("</ul>");
        }

        writer.println("<input type=\"hidden\" name=\"qid\" value=\"" + quiz.id() + "\" />");
        writer.println("<input type=\"hidden\" name=\"ssc\" value=\"" + section.sc() + "\" />");
        writer.println("<input type=\"hidden\" name=\"isc\" value=\"" + item.sc() + "\" />");
        writer.println("<button type=\"submit\">Check</button>");
        writer.println("</form>");
      }

      writer.println("</section>");
    }
    
    writer.println("</body>");
    writer.println("</html>");
    writer.flush();
  }

  public static void htmlOf(OutFullQuiz quiz, PrintWriter writer) throws IOException {
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
    writer.println("iframe{border:none}");
    writer.println(".correct{color:green}");
    writer.println(".incorrect{color:red}");
    writer.println("</style>");
    writer.println("</head>");
    writer.println("<body>");
    
    writer.append("<h1>").append(quiz.id()).append("</h1>");
    writer.append(md.apply(quiz.intro()));

    val p = Pattern.compile("\\{\\{\\d+}}");

    for (val section : quiz.sections()) {
      writer.println("<hr class=\"solid\"/>");
      writer.append("<h2>").append(section.title()).append("</h2>\n");
      writer.append(md.apply(section.intro()));

      for (val item : section.items()) {
        writer.println("<form method=\"post\" action=\"http://localhost:10000/check?item=" + 
              item.sc() + "\" enctype=\"application/x-www-form-urlencoded\" target=\"itemres" + item.sc() + "\">");
        //writer.println("<fieldset>");
        val def = md.apply(item.definition().text());
        val sb = new StringBuilder();
        var hintsShown = false;
        var input = "";
        if (item.hintsVisible() && item.definition().text().indexOf("{{") > -1) {
          input = "<select name=\"answer\">";
          for (val hint : item.hints())
            input += "<option>" + hint.get(0).text() + "</option>";
          input += "</select>";
          hintsShown = true;
        } else {
          input = "<input type=\"text\" name=\"answer\"/>";
        }
        val matcher = p.matcher(def);
        while (matcher.find()) {
          val idx = Integer.parseInt(matcher.group(0).replace("{{", "").replace("}}", ""));
          val value = item.hints().get(idx - 1).get(0).text();
          matcher.appendReplacement(sb, input.replace("~ans~", "ans[]") + 
                "<span class=\"gray\">(" + value + ")</span>");
        }
        matcher.appendTail(sb);
        writer.println("<hr class=\"dotted\"/>");
        writer.append(sb.toString());
        val name = "ans[]";
        if (item.hintsVisible() && !hintsShown) {

          val radio = item.solutions().size() == 1 ? "radio" : "checkbox";
          writer.println("<ul>");
          int c = 0;
          for (val hint : item.hints()) {
            val id = "choice" + (c++);
            writer.append("<li>").append("<input name=\"").append("answer").append("\" type=\"")
              .append(radio).append("\" id=\"").append(id + item.sc()).append("\" value=\"1\"><label for=\"")
                .append(id + item.sc()).append("\">").append(hint.get(0).text()).append("</label></li>\n");
          }
          writer.println("</ul>");
        }
        writer.println("<button type=\"sumbit\">Check</button>");
        writer.println("<iframe name=\"itemres" + item.sc() + "\" src='file:///tmp/correct.html'></iframe>");
        //writer.println("</fieldset>");
        writer.println("</form>");
      }

    }

    writer.println("</body>");
    writer.println("</html>");
  }

}
