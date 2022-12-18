package author.panes.quiz;

import javafx.fxml.Initializable;
import javafx.scene.Node;

import author.util.Bus;
import author.dtos.*;

public class Quizzes implements Initializable {

  public interface UIMessage {}
  public static final UIMessage CLEAR_CREATE_PANE = new UIMessage() {};
  public static final UIMessage SHOW_CREATE_PANE = new UIMessage() {};
  public static final UIMessage HIDE_CREATE_PANE = new UIMessage() {};
  @lombok.Value
  public static class ShowQuiz implements UIMessage {
    OutQuizListed quiz;
  }

  private Bus<UIMessage, UIMessage> uiBus;

  private Node listPane;
  private Node createPane;
  private Node form;
  private Node buttonBox;




  @Override
  public void initialize(java.net.URL location, java.util.ResourceBundle resources) {

  }

}
