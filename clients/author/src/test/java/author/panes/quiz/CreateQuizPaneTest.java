package author.panes.quiz;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.*;
import org.testfx.matcher.control.*;
import org.testfx.assertions.api.*;
import org.testfx.util.*;

import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import static org.mockito.Mockito.*;
import static org.testfx.api.FxAssert.*;
import static org.testfx.assertions.api.Assertions.*;

import io.reactivex.rxjava3.core.Observable;

import author.panes.quiz.CreateQuizPane;
import author.events.ApiResponse;
import author.requests.ApiRequest;
import author.util.Factories;
import author.dtos.*;

import author.testutil.*;
import author.TestData;

import java.util.Set;
import java.util.List;

import lombok.val;

@ExtendWith(ApplicationExtension.class)
@DisplayName("Create quiz pane")
class CreateQuizPaneTest {

  TestBus<ApiResponse, ApiRequest> apiBus = new TestBus<>();
  TestBus<Quizzes.UIMessage, Quizzes.UIMessage> uiBus = new TestBus<>();

  @Start
  private void start(Stage stage) throws Exception {
    Node sut = Factories.nodeWith(new CreateQuizPane(apiBus, uiBus));
    stage.setScene(new Scene((Parent) sut, 500, 250));
    stage.show();
  }

  void isClear(FxRobot robot) {
    Lookup lu = new Lookup(robot);

    assertThat(lu.input("#title")).hasText("");
    assertThat(lu.input("#id")).hasText("");
    assertThat(lu.list("#authors")).isEmpty();
    assertThat(lu.list("#inspectors")).isEmpty();

    assertThat(lu.combo("#selectedAuthor").getValue()).isNull();
    assertThat(lu.combo("#selectedInspector").getValue()).isNull();
  }

  @Test @DisplayName("clears on clear message")
  void clears(FxRobot robot) {
    robot
      .clickOn("#id").write("abc")
      .clickOn("#title").write("abc")
      ;
    uiBus.emulIn(Quizzes.CLEAR_CREATE_PANE);
    isClear(robot);
  }

  private void putStaff() {
    apiBus.emulIn(new ApiResponse.PersonList(List.of(
      TestData.curator, TestData.author1, TestData.author2, TestData.inspector1, TestData.inspector2)));
  }

  @Test @DisplayName("fills combos with staff")
  void fillCombos(FxRobot robot) {
    Lookup lu = new Lookup(robot);
    putStaff();
    assertThat(lu.<OutPerson>combo("#selectedAuthor"))
      .containsExactlyItemsInOrder(
        TestData.curator,
        TestData.author1,
        TestData.author2,
        TestData.inspector1,
        TestData.inspector2
      )
      ;
    assertThat(lu.<OutPerson>combo("#selectedInspector"))
      .containsExactlyItemsInOrder(
        TestData.curator,
        TestData.author1,
        TestData.author2,
        TestData.inspector1,
        TestData.inspector2
      )
      ;
  }

  @Test @DisplayName("clears and emits hide event on cancel button")
  void hideOnCancel(FxRobot robot) {
    robot
      .clickOn("#id").write("abc")
      .clickOn("#title").write("abc")
      ;
    robot.clickOn("#cancel");
    isClear(robot);
    assertThat(uiBus.poll()).isSameAs(Quizzes.HIDE_CREATE_PANE);
  }

  @Test @DisplayName("sends create request")
  void create(FxRobot robot) {
    putStaff();
    robot
      .clickOn("#id").write("TQ")
      .clickOn("#title").write("the TQ title")
      .clickOn("#selectedAuthor")
        .type(KeyCode.DOWN, 1)
        .type(KeyCode.ENTER)
        .clickOn("#addAuthor")
      .clickOn("#selectedInspector")
        .type(KeyCode.DOWN, 3)
        .type(KeyCode.ENTER)
        .clickOn("#addInspector")
      .clickOn("#save")
      ;
    assertThat(apiBus.poll()).isEqualTo(new ApiRequest.Create("TQ",
      "the TQ title", Set.of("author1"), Set.of("inspector1")));
  }

}
