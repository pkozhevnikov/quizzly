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

import java.util.List;

import static org.mockito.Mockito.*;
import static org.testfx.api.FxAssert.*;
import static org.hamcrest.Matchers.*;
import static org.testfx.assertions.api.Assertions.*;

import author.panes.quiz.ListPane;
import author.panes.quiz.Quizzes;
import author.events.ApiResponse;
import author.requests.ApiRequest;
import author.util.*;
import author.dtos.*;

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
      .hasExactlyNumRows(4)
      .containsRow("q1", "q1", "q1 title", "Composing", "")
      .containsRow("q2", "q2", "q2 title", "Review", "")
      .containsRow("q3", "q3", "q3 title", "Released", "+")
      .containsRow("q4", "q4", "q4 title", "Released", "")
      ;
    apiBus.emulIn(new ApiResponse.QuizList(TestData.list));
    assertThat(robot.lookup("#list").queryTableView()).hasExactlyNumRows(4);
  }

  @Test @DisplayName("sends 'goto quiz' message on link click")
  void gotoQuiz(FxRobot robot) {
    apiBus.emulIn(new ApiResponse.QuizList(TestData.list));
    val go = robot.from(robot.lookup("q3 title").query().getParent()).lookup(".goto-quiz").query();
    robot.clickOn(go);
    assertThat(apiBus.poll()).isEqualTo(new ApiRequest.GetQuiz("q3"));
  }

  @Test @DisplayName("reacts on got obsolete event")
  void obsoleteEvent(FxRobot robot) {
    Lookup lu = new Lookup(robot);
    apiBus.emulIn(new ApiResponse.QuizList(TestData.list));
    assertThat(lu.table("#list"))
      .containsRow("q4", "q4", "q4 title", "Released", "");
    apiBus.emulIn(new ApiResponse.GotObsolete("q4"));
    assertThat(lu.table("#list"))
      .containsRow("q4", "q4", "q4 title", "Released", "+");
  }

  @Test @DisplayName("reacts on addition/removal authors and inspectors")
  void addRemoveEvents(FxRobot robot) {
    val list = robot.lookup("#list").<OutQuizListed>queryTableView().getItems();
    apiBus.emulIn(new ApiResponse.PersonList(List.of(TestData.curator, TestData.author1, 
      TestData.author2, TestData.author3,
      TestData.inspector1, TestData.inspector2, TestData.inspector3)));
    apiBus.emulIn(new ApiResponse.QuizList(TestData.list));
    assertThat(list.get(0).authors())
      .containsExactlyInAnyOrder(TestData.author1, TestData.author2);
    apiBus.emulIn(new ApiResponse.AuthorAdded("q1", TestData.author3.id()));
    assertThat(list.get(0).authors())
      .containsExactlyInAnyOrder(TestData.author1, TestData.author2, TestData.author3);
    apiBus.emulIn(new ApiResponse.AuthorRemoved("q1", TestData.author1.id()));
    assertThat(list.get(0).authors())
      .containsExactlyInAnyOrder(TestData.author3, TestData.author2);

    assertThat(list.get(0).inspectors())
      .containsExactlyInAnyOrder(TestData.inspector1, TestData.inspector2);
    apiBus.emulIn(new ApiResponse.InspectorAdded("q1", TestData.inspector3.id()));
    assertThat(list.get(0).inspectors())
      .containsExactlyInAnyOrder(TestData.inspector1, TestData.inspector2, TestData.inspector3);
    apiBus.emulIn(new ApiResponse.InspectorRemoved("q1", TestData.inspector1.id()));
    assertThat(list.get(0).inspectors())
      .containsExactlyInAnyOrder(TestData.inspector2, TestData.inspector3);
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
    assertThat(list).hasExactlyNumRows(4);
    apiBus.emulIn(new ApiResponse.QuizAdded(TestData.newQuiz));
    assertThat(list)
      .hasExactlyNumRows(5)
      .containsRow("q5", "q5", "q5 title", "Composing")
      ;
    assertThat(uiBus.poll()).isEqualTo(new Quizzes.ShowQuiz(TestData.newQuiz));
  }
    
}
