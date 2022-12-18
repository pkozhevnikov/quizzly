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
@DisplayName("Create button box")
class CreateButtonBoxTest {

  TestBus<ApiResponse, ApiRequest> apiBus = new TestBus<>();
  TestBus<Quizzes.UIMessage, Quizzes.UIMessage> uiBus = new TestBus<>();

  @Start
  private void start(Stage stage) throws Exception {
    Node sut = new CreateButtonBox(apiBus, uiBus);
    stage.setScene(new Scene((Parent) sut, 400, 40));
    stage.show();
  }

  @Test @DisplayName("send 'show create form' message on button click")
  void createButton(FxRobot robot) {
    robot.clickOn(robot.lookup("#create").queryButton());
    assertThat(uiBus.poll()).isSameAs(Quizzes.SHOW_CREATE_PANE);
  }

  @Test @DisplayName("send 'get list' request on button click")
  void refreshButton(FxRobot robot) {
    robot.clickOn(robot.lookup("#refresh").queryButton());
    assertThat(apiBus.poll()).isSameAs(ApiRequest.GET_LIST);
  }
}
