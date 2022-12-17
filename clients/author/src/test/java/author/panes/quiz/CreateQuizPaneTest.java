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
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import static org.mockito.Mockito.*;
import static org.testfx.api.FxAssert.*;
import static org.hamcrest.Matchers.*;
import static org.testfx.assertions.api.Assertions.*;

import io.reactivex.rxjava3.core.Observable;

import author.panes.quiz.CreateQuizPane;
import author.events.ApiResponse;
import author.requests.ApiRequest;

import author.testutil.*;
import author.TestData;

import java.util.Set;
import java.util.List;

@ExtendWith(ApplicationExtension.class)
@DisplayName("Create quiz pane")
class CreateQuizPaneTest {

  TestBus<ApiResponse, ApiRequest> apiBus = new TestBus<>();
  TestBus<ListPane.UIMessage, ListPane.UIMessage> uiBus = new TestBus<>();

  @Start
  private void start(Stage stage) throws Exception {
    Node sut = CreateQuizPane.create(uiBus, apiBus);
    stage.setScene(new Scene((Parent) sut, 500, 250));
    stage.show();
  }

  void isClear(FxRobot robot) {
    Lookup lu = new Lookup(robot);

    assertThat(lu.input("#title")).hasText("");
    assertThat(lu.input("#id")).hasText("");
    assertThat(lu.list("#authors")).isEmpty();
    assertThat(lu.list("#inspectors")).isEmpty();

    //assertThat(lu.combo("#selectedAuthor")).doesNotHaveSelectedItem();
    //assertThat(lu.combo("#selectedInspector")).doesNotHaveSelectedItem();
  }

  @Test @DisplayName("clears itself on clear message")
  void clears(FxRobot robot) {
    robot
      .clickOn("#id").write("abc")
      .clickOn("#title").write("abc")
      ;
    uiBus.emulIn(ListPane.CLEAR_CREATE_PANE);
    isClear(robot);
  }

  @Test @DisplayName("fills combos with staff")
  void fillCombos(FxRobot robot) {
    Lookup lu = new Lookup(robot);
    apiBus.emulIn(new ApiResponse.PersonList(List.of(
      TestData.curator, TestData.author1, TestData.author2, TestData.inspector1, TestData.inspector2)));
    assertThat(lu.list("#authors"))
      .hasExactlyNumItems(5)
      .hasListCell(TestData.curator)
      .hasListCell(TestData.author1)
      .hasListCell(TestData.author2)
      .hasListCell(TestData.inspector1)
      .hasListCell(TestData.inspector2)
      ;
    assertThat(lu.list("#inspectors"))
      .hasExactlyNumItems(5)
      .hasListCell(TestData.curator)
      .hasListCell(TestData.author1)
      .hasListCell(TestData.author2)
      .hasListCell(TestData.inspector1)
      .hasListCell(TestData.inspector2)
      ;
  }

  @Test @DisplayName("clears and emits hide event on cancel button")
  void hideOnCancel(FxRobot robot) {
    robot.clickOn("#cancel");
    isClear(robot);
    //assertThat(uiBus.poll()).isSame(ListPane.HIDE_CREATE_PANE);
  }

  @Test @DisplayName("sends create request")
  void create(FxRobot robot) {
    //robot
    //  .clickOn("#id").write("TQ")
    //  .clickOn("#title").write("the TQ title")
    //  .clickOn("#selectedAuthor").clickOn(ListViewMatchers.containsItem(TestData.author1))
    //    .clickOn("#addAuthor")
    //  .clickOn("#selectedAuthor").clickOn(ListViewMatchers.containsItem(TestData.author2))
    //    .clickOn("#addAuthor")
    //  .clickOn("#selectedInspector").clickOn(ListViewMatchers.containsItem(TestData.inspector1))
    //    .clickOn("#addInspector")
    //  .clickOn("#selectedInspector").clickOn(ListViewMatchers.containsItem(TestData.inspector2))
    //    .clickOn("#addInspector")
    //  .clickOn("#save")
    //  ;
    //assertThat(apiBus.poll()).isEqualTo(new ApiRequest.Create("TQ",
    //  "the TQ title", Set.of("author1", "author2"), Set.of("inspector1", "inspector2")));
  }

}
