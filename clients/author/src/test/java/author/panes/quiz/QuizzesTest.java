package author.panes.quiz;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.*;
import org.testfx.matcher.control.*;
import org.testfx.assertions.api.*;

import javafx.scene.*;
import javafx.stage.Stage;

import static org.testfx.assertions.api.Assertions.*;

import author.panes.quiz.CreateButtonBox;
import author.events.ApiResponse;
import author.requests.ApiRequest;

import author.testutil.*;
import author.TestData;

import lombok.val;

@ExtendWith(ApplicationExtension.class)
@DisplayName("Quizzes pane")
class QuizzesTest {

  TestBus<ApiResponse, ApiRequest> apiBus = new TestBus<>();

  @Start
  private void start(Stage stage) throws Exception {
    Node sut = Quizzes.create(apiBus);
    stage.setScene(new Scene((Parent) sut, 1000, 700));
    stage.show();
  }


  @Test @DisplayName("shows create form on button click")
  void showCreatePane(FxRobot robot) {
    robot.clickOn(robot.lookup("#buttonsBox").lookup("#create").queryButton());
    assertThat(robot.lookup("#buttonsBox").tryQuery()).isEmpty();
    assertThat(robot.lookup("#createPane").tryQuery()).isPresent();
  }

  @Test @DisplayName("hides create form on button click")
  void hideCreatePane(FxRobot robot) {
    robot.clickOn(robot.lookup("#buttonsBox").lookup("#create").queryButton());
    robot.clickOn(robot.lookup("#createPane").lookup("#cancel").queryButton());
    assertThat(robot.lookup("#buttonsBox").tryQuery()).isPresent();
    assertThat(robot.lookup("#createPane").tryQuery()).isEmpty();
  }

  @Test @DisplayName("displays quiz list and shows quiz in form")
  void displayQuiz(FxRobot robot) {
    apiBus.emulIn(new ApiResponse.QuizList(TestData.list));
    robot.clickOn(robot.lookup("#mainList").lookup(LabeledMatchers.hasText("q2")).queryLabeled());
    assertThat(robot.lookup("#form").lookup("#status").queryLabeled()).hasText("Review");
  }

  @Test @DisplayName("hides create form on creation event")
  void hideCreatePaneWhenCreated(FxRobot robot) {
    robot.clickOn("#create");
    assertThat(robot.lookup("#createPane").tryQuery()).isPresent();
    apiBus.emulIn(new ApiResponse.QuizAdded(TestData.newQuiz));
    assertThat(robot.lookup("#createPane").tryQuery()).isEmpty();
  }

}

