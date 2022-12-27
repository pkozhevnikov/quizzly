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
@DisplayName("Section pane")
class SectionPaneTest {

  TestBus<ApiResponse, ApiRequest> apiBus = new TestBus<>();
  TestBus<MainUIMessage, MainUIMessage> uiBus = new TestBus<>();

  @Start
  private void start(Stage stage) throws Exception {
    val sut = new SectionPane(apiBus, uiBus);
    val scene = new Scene(sut, 1000, 700);
    scene.getStylesheets().add("/author/common.css");
    stage.setScene(scene);
    stage.show();
  }

  void putTestSection() {
    apiBus.emulIn(new ApiResponse.SectionOwned("q1", TestData.section1));
  }


  @Test @DisplayName("sets data correctly on 'edit' click")
  void setData(FxRobot robot) throws Exception {
    putTestSection();
    assertThat(robot.lookup("#title").queryTextInputControl()).hasText("section 1 title");
    assertThat(robot.lookup("#intro").queryTextInputControl()).hasText("section 1 intro");
    assertThat(robot.lookup("#itemsBox").queryParent()).hasExactlyNumChildren(3);
    assertThat(robot.lookup(".item").nth(0).lookup("#definition").queryTextInputControl())
      .hasText("item 1 definition");
  }

  @Test @DisplayName("sends correct request on save click")
  void saveChanges(FxRobot robot) throws Exception {
    putTestSection();
    robot
      .clickOn("#title").press(KeyCode.CONTROL).type(KeyCode.A).release(KeyCode.CONTROL)
        .write("title plus")
      .clickOn("#intro").press(KeyCode.CONTROL).type(KeyCode.A).release(KeyCode.CONTROL)
        .write("intro plus")
      .clickOn("Save changes")
      ;
    assertThat(apiBus.poll()).isEqualTo(new ApiRequest.UpdateSection("q1-1", "title plus", "intro plus"));
  }

  @Test @DisplayName("sends add item request")
  void addItemRequest(FxRobot robot) {
    putTestSection();
    robot.clickOn("Add item");
    assertThat(apiBus.poll()).isEqualTo(new ApiRequest.AddItem("q1-1"));
  }

  @Test @DisplayName("reacts on add item response")
  void addItemResponse(FxRobot robot) {
    putTestSection();
    apiBus.emulIn(new ApiResponse.ItemAdded("q1-1", "q1-1-4"));
    assertThat(robot.lookup("#itemsBox").queryParent()).hasExactlyNumChildren(4);
    assertThat(robot.lookup(".item").nth(3).lookup("#definition").queryTextInputControl()).hasText("");
  }

  @Test @DisplayName("sends discharge request")
  void dischargeRequest(FxRobot robot) {
    putTestSection();
    robot.clickOn("Discharge");
    assertThat(apiBus.poll()).isEqualTo(new ApiRequest.DischargeSection("q1-1"));
  }

  @Test @DisplayName("reacts on item removal response")
  void removeItem(FxRobot robot) {
    putTestSection();
    assertThat(robot.lookup("#itemsBox").queryParent()).hasExactlyNumChildren(3);
    apiBus.emulIn(new ApiResponse.ItemRemoved("q2-1", "2"));
    assertThat(robot.lookup("#itemsBox").queryParent()).hasExactlyNumChildren(3);
    apiBus.emulIn(new ApiResponse.ItemRemoved("q1-1", "2"));
    assertThat(robot.lookup("#itemsBox").queryParent()).hasExactlyNumChildren(2);
    assertThat(itemDefinition(0, robot)).hasText("item 1 definition");
    assertThat(itemDefinition(1, robot)).hasText("item 3 definition");
  }

  TextInputControl itemDefinition(int itemIndex, FxRobot robot) {
    return robot.lookup(".item").nth(itemIndex).lookup("#definition").queryTextInputControl();
  }

  @Test @DisplayName("reacts on item move")
  void itemMoved(FxRobot robot) throws Exception {
    putTestSection();
    apiBus.emulIn(new ApiResponse.ItemMoved("q1-1", List.of("2", "1", "3")));
    assertThat(itemDefinition(0, robot)).hasText("item 2 definition");
    assertThat(itemDefinition(1, robot)).hasText("item 1 definition");
    assertThat(itemDefinition(2, robot)).hasText("item 3 definition");
  }

}
