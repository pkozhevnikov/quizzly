package author.panes.section;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.params.*;
import org.testfx.api.FxRobot;
import org.testfx.robot.Motion;
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
import static org.testfx.util.WaitForAsyncUtils.*;
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
@DisplayName("Item control")
class ItemTest {

  TestBus<ApiResponse, ApiRequest> apiBus = new TestBus<>();

  Item sut;


  @Start
  private void start(Stage stage) throws Exception {
    sut = new Item("testSC", empty, apiBus.out());
    val scene = new Scene((Parent) sut, 1000, 700);
    scene.getStylesheets().add("/author/common.css");
    stage.setScene(scene);
    stage.show();
  }

  OutItem empty = new OutItem("it1", "", new OutStatement("", null), List.of(), false, List.of());

  @Test @DisplayName("add hint")
  void addHint(FxRobot robot) {
    asyncFx(() -> sut.setData("testSC", empty));
    assertThat(robot.lookup("#hints").queryParent()).hasExactlyNumChildren(0);
    robot.clickOn("Add hint");
    assertThat(robot.lookup("#hints").queryParent()).hasExactlyNumChildren(1);
  }

  private static Stream<Arguments> apiRequests_args() {
    return Stream.of(
      Arguments.of("Move up", new ApiRequest.MoveItem("testSC", "it1", true)),
      Arguments.of("Move down", new ApiRequest.MoveItem("testSC", "it1", false)),
      Arguments.of("Remove", new ApiRequest.RemoveItem("testSC", "it1"))
    );
  }

  @ParameterizedTest(name = "{0}") @DisplayName("sends correct api requests")
  @MethodSource("apiRequests_args")
  void apiRequests(String buttonCaption, ApiRequest expected, FxRobot robot) {
    robot.clickOn(buttonCaption);
    assertThat(apiBus.poll()).isEqualTo(expected);
  }
  
  @Test @DisplayName("sends correct save request")
  void saveRequest(FxRobot robot) {
    robot.clickOn("#definition")
      .write("the definition")
      .clickOn("Add hint")
      .clickOn(robot.lookup(".hint").nth(0)
        .lookup(".hint-alt").nth(0).lookup(".text-field").queryTextInputControl())
      .write("hint 1 alt 1")
      .clickOn(robot.lookup(".hint").nth(0)
        .lookup(".hint-alt").nth(0).lookup(".button").queryButton())
      .clickOn(robot.lookup(".hint").nth(0)
        .lookup(".hint-alt").nth(1).lookup(".text-field").queryTextInputControl())
      .write("hint 1 alt 2")
      .clickOn("Add hint")
      .clickOn(robot.lookup(".hint").nth(1)
        .lookup(".hint-alt").nth(0).lookup(".text-field").queryTextInputControl())
      .write("hint 2 alt 1")
      .clickOn("#hintsVisible")
      .clickOn(robot.lookup(".hint").nth(0)
        .lookup(".check-box").queryAs(CheckBox.class))
      .clickOn("Save item")
      ;
    assertThat(apiBus.poll()).isEqualTo(new ApiRequest.SaveItem("testSC", expected));
  }

  OutItem expected = new OutItem("it1", "", new OutStatement("the definition", null), 
    List.of(
      List.of(
        new OutStatement("hint 1 alt 1", null),
        new OutStatement("hint 1 alt 2", null)
      ),
      List.of(
        new OutStatement("hint 2 alt 1", null)
      )
    ),
    true,
    List.of(0)
  );

  @Test @DisplayName("initializes correctly")
  void intialization(FxRobot robot) throws Exception {
    asyncFx(() -> sut.setData("tst", expected));
    waitForFxEvents();
    assertThat(robot.lookup("#hints").queryParent()).hasExactlyNumChildren(2);
    assertThat(robot.lookup("#hintsVisible").queryAs(CheckBox.class).isSelected()).isTrue();
    assertThat(robot.lookup(".hint").nth(0)
      .lookup(".text-field").nth(0).queryTextInputControl()).hasText("hint 1 alt 1");
    assertThat(robot.lookup(".hint").nth(0).lookup(".alts").queryParent()).hasExactlyNumChildren(2);
    assertThat(robot.lookup(".hint").nth(0).lookup(".check-box").queryAs(CheckBox.class).isSelected()).isTrue();
    assertThat(robot.lookup(".hint").nth(1).lookup(".alts").queryParent()).hasExactlyNumChildren(1);
    assertThat(robot.lookup(".hint").nth(1).lookup(".check-box").queryAs(CheckBox.class).isSelected()).isFalse();
    assertThat(robot.lookup("#definition").queryTextInputControl()).hasText("the definition");
  }

  @Test @DisplayName("removes hint")
  void removeHint(FxRobot robot) {
    asyncFx(() -> sut.setData("tst", expected));
    waitForFxEvents();
    assertThat(robot.lookup("#hints").queryParent()).hasExactlyNumChildren(2);
    clickContextMenu(0, "Remove", robot);
    assertThat(robot.lookup("#hints").queryParent()).hasExactlyNumChildren(1);
    assertThat(firstHintAlt(0, robot)).hasText("hint 2 alt 1");
    assertThat(hintIndexCaption(0, robot)).isEqualTo("1");
  }

  String hintIndexCaption(int hintIndex, FxRobot robot) {
    return robot.lookup(".hint").nth(hintIndex)
      .lookup(".check-box").queryAs(CheckBox.class).getText();
  }

  void clickContextMenu(int hintIdx, String button, FxRobot robot) {
    robot.clickOn(robot.lookup(".hint").nth(hintIdx)
        .lookup(".check-box").queryAs(CheckBox.class), Motion.DEFAULT, MouseButton.SECONDARY)
      .clickOn(robot.lookup(".menu-item").lookup(button).queryLabeled())
      ;
  }

  TextInputControl firstHintAlt(int hintIdx, FxRobot robot) {
    return robot.lookup(".hint").nth(hintIdx)
      .lookup(".text-field").queryTextInputControl();
  }

  @Test @DisplayName("moves hint up")
  void moveHintUp(FxRobot robot) {
    asyncFx(() -> sut.setData("tst", expected));
    waitForFxEvents();
    clickContextMenu(1, "Move up", robot);
    assertThat(firstHintAlt(0, robot)).hasText("hint 2 alt 1");
    assertThat(firstHintAlt(1, robot)).hasText("hint 1 alt 1");
    assertThat(hintIndexCaption(0, robot)).isEqualTo("1");
    assertThat(hintIndexCaption(1, robot)).isEqualTo("2");
    clickContextMenu(0, "Move up", robot);
    assertThat(firstHintAlt(0, robot)).hasText("hint 2 alt 1");
    assertThat(firstHintAlt(1, robot)).hasText("hint 1 alt 1");
  }

  @Test @DisplayName("moves hint down")
  void moveHintDown(FxRobot robot) {
    asyncFx(() -> sut.setData("tst", expected));
    waitForFxEvents();
    clickContextMenu(0, "Move down", robot);
    assertThat(firstHintAlt(0, robot)).hasText("hint 2 alt 1");
    assertThat(firstHintAlt(1, robot)).hasText("hint 1 alt 1");
    assertThat(hintIndexCaption(0, robot)).isEqualTo("1");
    assertThat(hintIndexCaption(1, robot)).isEqualTo("2");
    clickContextMenu(1, "Move down", robot);
    assertThat(firstHintAlt(0, robot)).hasText("hint 2 alt 1");
    assertThat(firstHintAlt(1, robot)).hasText("hint 1 alt 1");
  }
    

}

