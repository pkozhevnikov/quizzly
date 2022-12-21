package author.panes;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.params.*;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.*;
import org.testfx.matcher.control.*;
import org.testfx.assertions.api.*;

import javafx.scene.*;
import javafx.stage.Stage;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.input.*;

import java.util.*;
import java.util.stream.*;
import java.util.function.*;

import static org.testfx.assertions.api.Assertions.*;
import org.assertj.core.api.*;

import author.events.*;
import author.requests.*;
import author.util.*;
import author.dtos.*;
import author.messages.*;

import author.testutil.*;
import author.TestData;

import lombok.val;

@ExtendWith(ApplicationExtension.class)
@DisplayName("Main pane")
class MainPaneTest {

  TestBus<ApiResponse, ApiRequest> apiBus = new TestBus<>();
  TestBus<LoginEvent, LoginRequest> loginBus = new TestBus<>();


  @Start
  private void start(Stage stage) throws Exception {
    Node sut = new MainPane(apiBus, loginBus);
    stage.setScene(new Scene((Parent) sut, 1000, 700));
    stage.show();
  }

  @Test @DisplayName("updated on login")
  void updatedOnLogin(FxRobot robot) throws Exception {
    val onGetList = apiBus.on(ApiRequest.GET_LIST, new ApiResponse.QuizList(TestData.list));
    loginBus.emulIn(new LoginEvent.Success("author1", TestData.author1));
    assertThat(robot.lookup("#username").queryLabeled()).hasText("author1 name");
    assertThat(robot.lookup("#mainList").queryTableView()).hasExactlyNumRows(4);

    robot.clickOn(LabeledMatchers.hasText("q2"));
    assertThat(robot.lookup("#form").lookup("#status").queryLabeled()).hasText("Review");

    onGetList.free();
  }

  @Test @DisplayName("goes to selected quiz and returns back")
  void gotoQuiz(FxRobot robot) {
    val onGetQuiz = apiBus.on(new ApiRequest.GetQuiz("q3"), 
      new ApiResponse.FullQuiz(TestData.fullQuiz1.withState("XYZ")));
    val onGetList = apiBus.on(ApiRequest.GET_LIST, new ApiResponse.QuizList(TestData.list));
    loginBus.emulIn(new LoginEvent.Success("author1", TestData.author1));
    val go = robot.from(robot.lookup("q3 title").query().getParent()).lookup(".goto-quiz").query();
    robot.clickOn(go);
    assertThat(robot.lookup("#quizzes").tryQuery()).isEmpty();
    assertThat(robot.lookup("#quizPane").tryQuery()).isPresent();
    assertThat(robot.lookup("#quizPane").lookup("#status").queryLabeled()).hasText("XYZ");
    onGetList.free();
    onGetQuiz.free();

    robot.clickOn("#home");
    assertThat(robot.lookup("#quizzes").tryQuery()).isPresent();
    assertThat(robot.lookup("#quizPane").tryQuery()).isEmpty();

  }

  @Test @DisplayName("sends logout request on button click")
  void logout(FxRobot robot) {
    robot.clickOn("#logout");
    assertThat(loginBus.poll()).isSameAs(LoginRequest.LOGOUT);
  }

}
