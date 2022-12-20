package author.panes.quiz;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.*;
import org.testfx.matcher.control.*;
import org.testfx.assertions.api.*;

import javafx.scene.*;
import javafx.stage.Stage;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.stream.*;
import java.util.function.*;

import static org.testfx.assertions.api.Assertions.*;
import org.assertj.core.api.*;

import author.events.ApiResponse;
import author.requests.ApiRequest;
import author.util.*;
import author.dtos.*;
import author.messages.*;

import author.testutil.*;
import author.TestData;

import lombok.val;

@ExtendWith(ApplicationExtension.class)
@DisplayName("Quiz pane")
class QuizPaneTest {

  TestBus<ApiResponse, ApiRequest> apiBus = new TestBus<>();
  TestBus<MainUIMessage, MainUIMessage> uiBus = new TestBus<>();


  @Start
  private void start(Stage stage) throws Exception {
    Node sut = Factories.nodeWith(new QuizPane(apiBus, uiBus));
    stage.setScene(new Scene((Parent) sut, 1000, 700));
    stage.show();
  }

  private void checkReadiness(FxRobot robot, OutFullQuiz quiz) {
    val labels = robot.lookup("#readiness").queryAs(VBox.class).getChildren();
    assertThat(labels).allSatisfy(l -> assertThat(l.getStyleClass()).contains("check"));
    val textIs = quiz.readinessSigns().stream().map(a -> 
      (Consumer<Labeled>) l -> assertThat(l.getText()).isEqualTo(a.name()))
      .toArray(Consumer[]::new);
    assertThat(labels).satisfiesExactlyInAnyOrder(textIs);
  }

  private void checkApprovals(FxRobot robot, OutFullQuiz quiz) {
    val labels = robot.lookup("#approvals").queryAs(VBox.class).getChildren();
    val sats = Stream.concat(
      quiz.approvals().stream().map(i ->
        (Consumer<Labeled>) l -> {
          assertThat(l.getStyleClass()).contains("check");
          assertThat(l.getText()).isEqualTo(i.name());
        }
      ),
      quiz.disapprovals().stream().map(i ->
        (Consumer<Labeled>) l -> {
          assertThat(l.getStyleClass()).contains("uncheck");
          assertThat(l.getText()).isEqualTo(i.name());
        }
      )
    ).toArray(Consumer[]::new);
    assertThat(labels).satisfiesExactlyInAnyOrder(sats);
  }

  @Test @DisplayName("correct visual state on quiz set")
  void quizSet(FxRobot robot) {
    var lu = new Lookup(robot);
    uiBus.emulIn(new MainUIMessage.ActingAs(TestData.author1));
    uiBus.emulIn(new MainUIMessage.SetQuiz(TestData.fullQuiz1));
    assertThat(lu.label("#id")).hasText("q1");
    assertThat(lu.label("#status")).hasText("Composing");
    assertThat(lu.input("#title")).hasText("q1 title");
    assertThat(lu.input("#intro")).hasText("q1 intro");
    assertThat(lu.input("#recommendedLength")).hasText("65");
    assertThat(lu.label("#curatorName")).hasText("curator name");
    assertThat(lu.button("#save")).isEnabled();
    assertThat(lu.button("#setReady")).isEnabled();
    assertThat(lu.button("#unsetReady")).isDisabled();
    assertThat(lu.button("#approve")).isDisabled();
    assertThat(lu.button("#disapprove")).isDisabled();
    checkReadiness(robot, TestData.fullQuiz1);
    checkApprovals(robot, TestData.fullQuiz1);
    assertThat(lu.table("#sections").getItems()).containsExactly(
      TestData.section1, TestData.section2, TestData.section3);
  } 

  @Test @DisplayName("sends edit request on link click")
  void editClick(FxRobot robot) {
    uiBus.emulIn(new MainUIMessage.ActingAs(TestData.author1));
    uiBus.emulIn(new MainUIMessage.SetQuiz(TestData.fullQuiz1));
    val go = robot.from(robot.lookup("section 2 title").query().getParent()).lookup(".edit-section").query();
    robot.clickOn(go);
    assertThat(uiBus.poll()).isEqualTo(new MainUIMessage.EditSection(TestData.section2));
  }

  @Test @DisplayName("sends section up request on link click")
  void sectionUp(FxRobot robot) {
    uiBus.emulIn(new MainUIMessage.ActingAs(TestData.author1));
    uiBus.emulIn(new MainUIMessage.SetQuiz(TestData.fullQuiz1));
    val go = robot.from(robot.lookup("section 3 title").query().getParent()).lookup(".section-up").query();
    robot.clickOn(go);
    assertThat(apiBus.poll()).isEqualTo(new ApiRequest.MoveSection("q1-3", true));
  }

  @Test @DisplayName("sends section down request on link click")
  void sectionDown(FxRobot robot) throws Exception {
    uiBus.emulIn(new MainUIMessage.ActingAs(TestData.author1));
    uiBus.emulIn(new MainUIMessage.SetQuiz(TestData.fullQuiz1));
    val go = robot.from(robot.lookup("section 1 title").query().getParent()).lookup(".section-down").query();
    robot.clickOn(go);
    assertThat(apiBus.poll()).isEqualTo(new ApiRequest.MoveSection("q1-1", false));
  }

  @Test @DisplayName("sends remove section request on link click")
  void sectionRemove(FxRobot robot) {
    uiBus.emulIn(new MainUIMessage.ActingAs(TestData.author1));
    uiBus.emulIn(new MainUIMessage.SetQuiz(TestData.fullQuiz1));
    val go = robot.from(robot.lookup("section 2 title").query().getParent()).lookup(".remove-section").query();
    robot.clickOn(go);
    assertThat(apiBus.poll()).isEqualTo(new ApiRequest.RemoveSection("q1-2", "q1"));
  }

}

