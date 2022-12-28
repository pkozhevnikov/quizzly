package author;

import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.web.WebView;
import javafx.application.Application;
import javafx.embed.swing.SwingNode;
import javafx.scene.layout.StackPane;
import javafx.scene.control.ScrollPane;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.swing.*;

import lombok.val;

public class MDViewer extends Application {

  @Override public void start(Stage stage) throws Exception {
    val view = new SwingNode();
    initSwing(view);
    val parent = new StackPane();
    parent.getChildren().add(new ScrollPane(view));
    val scene = new Scene(parent, 500, 600);
    stage.setScene(scene);
    stage.show();
  }

  private static void initSwing(SwingNode node) {
    SwingUtilities.invokeLater(() -> {
      JEditorPane view = new JEditorPane();
      view.setEditable(false);
      view.setContentType("text/html");
      StringBuilder sb = new StringBuilder();
      sb.append("<html>");
      for (int i = 0; i<100; i++)
        sb.append("<h1>Hello ").append(i).append("</h1>");
      sb.append("</html>");

      val parser = Parser.builder().build();
      val doc = parser.parse("this is **markdown**");
      val renderer = HtmlRenderer.builder().build();
      
      view.setText(renderer.render(doc));
      node.setContent(view);
    });
  }

}
