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
import java.util.concurrent.CompletionException;

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
  TestBus<RootUIMessage, RootUIMessage> rootUiBus = new TestBus<>();


  @Start
  private void start(Stage stage) throws Exception {
    Node sut = new MainPane(apiBus, loginBus, rootUiBus);
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

    
    val onGetQuiz = apiBus.on(new ApiRequest.GetQuiz("q1"),
      new ApiResponse.FullQuiz(TestData.fullQuiz1));
    val onGetList = apiBus.on(ApiRequest.GET_LIST, new ApiResponse.QuizList(TestData.list));
    val onOwnSection = apiBus.on(new ApiRequest.OwnSection("q1", "q1-1"),
      new ApiResponse.SectionOwned("q1", TestData.section1));
    val onDischarge = apiBus.on(new ApiRequest.DischargeSection("q1-1"),
      new ApiResponse.SectionDischarged("q1-1"));
    loginBus.emulIn(new LoginEvent.Success("author1", TestData.author1));
    robot.clickOn(robot.lookup(".goto-quiz").nth(0).queryLabeled());
    robot.clickOn(robot.lookup(".edit-section").nth(0).queryLabeled());
    robot.clickOn("#logout");
    onGetQuiz.free();
    onGetList.free();
    onOwnSection.free();
    onDischarge.free();
    assertThat(apiBus.poll(6)).isEqualTo(new ApiRequest.DischargeSection("q1-1"));
    assertThat(loginBus.poll()).isSameAs(LoginRequest.LOGOUT);
    
  }

  @Test @DisplayName("goes to selected section")
  void gotoSection(FxRobot robot) throws Exception {
    val onGetQuiz = apiBus.on(new ApiRequest.GetQuiz("q1"),
      new ApiResponse.FullQuiz(TestData.fullQuiz1));
    val onGetList = apiBus.on(ApiRequest.GET_LIST, new ApiResponse.QuizList(TestData.list));
    loginBus.emulIn(new LoginEvent.Success("author1", TestData.author1));
    val onOwnSection = apiBus.on(new ApiRequest.OwnSection("q1", "q1-2"),
      new ApiResponse.SectionOwned("q1", TestData.section2));
    val onDischarge = apiBus.on(new ApiRequest.DischargeSection("q1-2"),
      new ApiResponse.SectionDischarged("q1-2"));
    val go = robot.from(robot.lookup("q1 title").query().getParent()).lookup(".goto-quiz").query();
    robot.clickOn(go);
    robot.clickOn(robot.lookup(".edit-section").nth(1).queryLabeled());
    assertThat(robot.lookup("#quizPane").tryQuery()).isEmpty();
    assertThat(robot.lookup("#sectionPane").tryQuery()).isPresent();
    onGetQuiz.free();
    onGetList.free();
    onOwnSection.free();
    onDischarge.free();

    assertThat(robot.lookup("#title").queryTextInputControl()).hasText("section 2 title");
  }

  @Test @DisplayName("goes to quiz pane on section discharge")
  void gotoQuizBackFromSection(FxRobot robot) throws Exception {
    val onGetQuiz = apiBus.on(new ApiRequest.GetQuiz("q1"),
      new ApiResponse.FullQuiz(TestData.fullQuiz1));
    val onGetList = apiBus.on(ApiRequest.GET_LIST, new ApiResponse.QuizList(TestData.list));
    val onOwnSection = apiBus.on(new ApiRequest.OwnSection("q1", "q1-1"),
      new ApiResponse.SectionOwned("q1", TestData.section1));
    val onDischarge = apiBus.on(new ApiRequest.DischargeSection("q1-1"),
      new ApiResponse.SectionDischarged("q1-1"));
    loginBus.emulIn(new LoginEvent.Success("author1", TestData.author1));
    robot.clickOn(robot.lookup(".goto-quiz").nth(0).queryLabeled());
    robot.clickOn(robot.lookup(".edit-section").nth(0).queryLabeled());
    robot.clickOn("Discharge");
    onGetQuiz.free();
    onGetList.free();
    onOwnSection.free();
    onDischarge.free();
    assertThat(robot.lookup("#quizPane").tryQuery()).isPresent();
    assertThat(robot.lookup("#sectionPane").tryQuery()).isEmpty();
  }

  @Test @DisplayName("auto discharge on back and logout buttons")
  void autoDischarge(FxRobot robot) {
    val onGetQuiz = apiBus.on(new ApiRequest.GetQuiz("q1"),
      new ApiResponse.FullQuiz(TestData.fullQuiz1));
    val onGetList = apiBus.on(ApiRequest.GET_LIST, new ApiResponse.QuizList(TestData.list));
    val onOwnSection = apiBus.on(new ApiRequest.OwnSection("q1", "q1-1"),
      new ApiResponse.SectionOwned("q1", TestData.section1));
    val onDischarge = apiBus.on(new ApiRequest.DischargeSection("q1-1"),
      new ApiResponse.SectionDischarged("q1-1"));
    loginBus.emulIn(new LoginEvent.Success("author1", TestData.author1));
    robot.clickOn(robot.lookup(".goto-quiz").nth(0).queryLabeled());
    robot.clickOn(robot.lookup(".edit-section").nth(0).queryLabeled());
    robot.clickOn("#home");
    onGetQuiz.free();
    onGetList.free();
    onOwnSection.free();
    onDischarge.free();
    assertThat(robot.lookup("#quizPane").tryQuery()).isPresent();
    assertThat(robot.lookup("#sectionPane").tryQuery()).isEmpty();
  }
    

  void assertMessage(String text, String color, FxRobot robot) {
    assertThat(robot.lookup("#message").queryLabeled())
      .hasText(text)
      .hasStyle("-fx-text-fill:" + color)
      ;
  }

  @Test @DisplayName("shows error messages from root bus")
  void showErrors(FxRobot robot) {
    assertThat(robot.lookup("#message").queryLabeled()).hasText("");
    rootUiBus.emulIn(new RootUIMessage.ApiError(new OutError(
      new OutErrorReason(100, "some api error"), List.of("clue1", "clue2"))));
    assertMessage("100: some api error [clue1, clue2]", "darkred", robot);
    rootUiBus.emulIn(new RootUIMessage.ProcessingError(new CompletionException(
      new Exception("could not complete"))));
    assertMessage("could not complete", "darkred", robot);
    rootUiBus.emulIn(new RootUIMessage.ProcessingError(new Exception("plain exception")));
    assertMessage("plain exception", "darkred", robot);
    rootUiBus.emulIn(RootUIMessage.ACCESS_DENIED);
    assertMessage("Access denied", "darkred", robot);
    rootUiBus.emulIn(RootUIMessage.NOT_LOGGED_IN);
    assertMessage("Not logged in", "darkred", robot);
    rootUiBus.emulIn(RootUIMessage.CLEAR);
    assertMessage("", "darkgreen", robot);
  }

    
}
