package author.panes.quiz;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.*;
import org.testfx.matcher.control.*;
import org.testfx.assertions.api.*;

import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import static org.mockito.Mockito.*;
import static org.testfx.api.FxAssert.*;
import static org.hamcrest.Matchers.*;
import static org.testfx.assertions.api.Assertions.*;

import author.panes.quiz.ListPane;
import author.panes.quiz.Quizzes;
import author.events.ApiResponse;
import author.requests.ApiRequest;
import author.util.*;

import author.testutil.*;
import author.TestData;

import lombok.val;

@ExtendWith(ApplicationExtension.class)
@DisplayName("List pane")
class ListPaneTest {

  TestBus<ApiResponse, ApiRequest> apiBus = new TestBus<>();
  TestBus<Quizzes.UIMessage, Quizzes.UIMessage> uiBus = new TestBus<>();

  @Start
  private void start(Stage stage) {
    Node sut = Factories.nodeWith(new ListPane(apiBus, uiBus));
    stage.setScene(new Scene((Parent) sut, 1000, 700));
    stage.show();
  }

  @Test @DisplayName("displays quiz list")
  void displayList(FxRobot robot) {
    Lookup lu = new Lookup(robot);
    apiBus.emulIn(new ApiResponse.QuizList(TestData.list));
    assertThat(robot.lookup("#list").queryTableView())
      .hasExactlyNumRows(3)
      .containsRow("q1", "q1 title", "Composing")
      .containsRow("q2", "q2 title", "Review")
      .containsRow("q3", "q3 title", "Released")
      ;
  }

  @Test @DisplayName("sends show quiz message on quiz selection")
  void showQuiz(FxRobot robot) throws Exception {
    apiBus.emulIn(new ApiResponse.QuizList(TestData.list));
    robot.clickOn(LabeledMatchers.hasText("q2"));
    assertThat(uiBus.poll()).isEqualTo(new Quizzes.ShowQuiz(TestData.list.get(1)));
    robot.press(KeyCode.CONTROL).clickOn(LabeledMatchers.hasText("q2"));
    assertThat(uiBus.poll()).isEqualTo(new Quizzes.ShowQuiz(null));
  }

  @Test @DisplayName("adds row when new quiz created")
  void addRow(FxRobot robot) {
    apiBus.emulIn(new ApiResponse.QuizList(TestData.list));
    val list = robot.lookup("#list").queryTableView();
    assertThat(list).hasExactlyNumRows(3);
    apiBus.emulIn(new ApiResponse.QuizAdded(TestData.newQuiz));
    assertThat(list)
      .hasExactlyNumRows(4)
      .containsRow("q4", "q4 title", "Composing")
      ;
    assertThat(uiBus.poll()).isEqualTo(new Quizzes.ShowQuiz(TestData.newQuiz));
  }
    
}
