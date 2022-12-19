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

import author.panes.quiz.QuizFormPane;
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
@DisplayName("Quiz form pane")
class QuizFormPaneTest {

  TestBus<ApiResponse, ApiRequest> apiBus = new TestBus<>();
  TestBus<Quizzes.UIMessage, Quizzes.UIMessage> uiBus = new TestBus<>();

  @Start
  private void start(Stage stage) throws Exception {
    Node sut = Factories.nodeWith(new QuizFormPane(apiBus, uiBus));
    stage.setScene(new Scene((Parent) sut, 400, 700));
    stage.show();
  }

  void isClear(FxRobot robot) {
    Lookup lu = new Lookup(robot);
    assertThat(lu.label("#status")).hasText("");
    assertThat(lu.label("#curatorName")).hasText("");
    assertThat(lu.list("#authors")).isEmpty();
    assertThat(lu.list("#inspectors")).isEmpty();
    assertThat(lu.combo("#selectedAuthor").getValue()).isNull();
    assertThat(lu.combo("#selectedInspector").getValue()).isNull();
    assertThat(lu.button("#setObsolete")).isDisabled();
    assertThat(lu.button("#addAuthor")).isDisabled();
    assertThat(lu.button("#addInspector")).isDisabled();
  }

  private void putStaff() {
    apiBus.emulIn(new ApiResponse.PersonList(List.of(
      TestData.curator, TestData.author1, TestData.author2, TestData.inspector1, TestData.inspector2)));
  }

  @Test @DisplayName("clear if set null quiz")
  void clearOnNull(FxRobot robot) {
    uiBus.emulIn(new Quizzes.ShowQuiz(null));
    isClear(robot);
  }

  @Test @DisplayName("fill combos with staff list")
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

  @Test @DisplayName("show quiz if set not null")
  void showQuiz(FxRobot robot) {
    val lu = new Lookup(robot);
    uiBus.emulIn(new Quizzes.ShowQuiz(TestData.list.get(1)));
    assertThat(lu.label("#status")).hasText("Review");
    assertThat(lu.label("#curatorName")).hasText("author1 name");
    assertThat(lu.<OutPerson>list("#authors").getItems())
      .containsExactlyInAnyOrder(TestData.curator, TestData.author2);
    assertThat(lu.<OutPerson>list("#inspectors").getItems())
      .containsExactlyInAnyOrder(TestData.inspector1, TestData.inspector2);
    assertThat(lu.button("#setObsolete")).isDisabled();
    assertThat(lu.button("#addAuthor")).isEnabled();
    assertThat(lu.button("#addInspector")).isEnabled();
  }

  @Test @DisplayName("set obsolete enabled or disabled depending on quiz status")
  void setObsoletButton(FxRobot robot) {
    val lu = new Lookup(robot);
    uiBus.emulIn(new Quizzes.ShowQuiz(TestData.list.get(0)));
    assertThat(lu.button("#setObsolete")).isDisabled();
    uiBus.emulIn(new Quizzes.ShowQuiz(TestData.list.get(1)));
    assertThat(lu.button("#setObsolete")).isDisabled();
    uiBus.emulIn(new Quizzes.ShowQuiz(TestData.list.get(2)));
    assertThat(lu.button("#setObsolete")).isDisabled();
    uiBus.emulIn(new Quizzes.ShowQuiz(TestData.list.get(3)));
    assertThat(lu.button("#setObsolete")).isEnabled();
  }

  @Test @DisplayName("sends 'set obsolete' request on button click")
  void setObsoleteRequest(FxRobot robot) {
    uiBus.emulIn(new Quizzes.ShowQuiz(TestData.list.get(3)));
    robot.clickOn(robot.lookup("#setObsolete").queryButton());
    assertThat(apiBus.poll()).isEqualTo(new ApiRequest.SetObsolete("q4"));
  }

  @Test @DisplayName("reacts on obsolete api event")
  void showObsolete(FxRobot robot) {
    val lu = new Lookup(robot);
    uiBus.emulIn(new Quizzes.ShowQuiz(TestData.list.get(3)));
    assertThat(lu.button("#setObsolete")).isEnabled();
    apiBus.emulIn(new ApiResponse.GotObsolete("another"));
    assertThat(lu.button("#setObsolete")).isEnabled();
    apiBus.emulIn(new ApiResponse.GotObsolete("q4"));
    assertThat(lu.button("#setObsolete")).isDisabled();
  }

  @Test @DisplayName("sends 'add author' request")
  void addAuthorRequest(FxRobot robot) {
    putStaff();
    uiBus.emulIn(new Quizzes.ShowQuiz(TestData.list.get(0)));
    robot
      .clickOn("#selectedAuthor")
        .type(KeyCode.DOWN, 3)
        .type(KeyCode.ENTER)
        .clickOn("#addAuthor")
        ;
    assertThat(apiBus.poll()).isEqualTo(new ApiRequest.AddAuthor("q1", "inspector1"));
  }

  @Test @DisplayName("displays new author on api event")
  void showNewAuthor(FxRobot robot) {
    putStaff();
    uiBus.emulIn(new Quizzes.ShowQuiz(TestData.list.get(0)));

    apiBus.emulIn(new ApiResponse.AuthorAdded("another", TestData.inspector1.id()));
    assertThat(robot.lookup("#authors").queryListView().getItems()).doesNotContain(TestData.inspector1);

    apiBus.emulIn(new ApiResponse.AuthorAdded("q1", TestData.inspector1.id()));
    assertThat(robot.lookup("#authors").queryListView().getItems()).contains(TestData.inspector1);
  }


  @Test @DisplayName("sends 'remove author' request")
  void removeAuthorRequest(FxRobot robot) {
    uiBus.emulIn(new Quizzes.ShowQuiz(TestData.list.get(0)));
    robot.clickOn(robot.lookup("#authors")
        .lookup(".list-cell").lookup(LabeledMatchers.hasText("author1 name")).queryParent()
          .lookup(".remove-item"))
          ;
    assertThat(apiBus.poll()).isEqualTo(new ApiRequest.RemoveAuthor("q1", "author1"));
  }

  @Test @DisplayName("removes author on api event")
  void showRemoveAuthor(FxRobot robot) {
    uiBus.emulIn(new Quizzes.ShowQuiz(TestData.list.get(0)));
    apiBus.emulIn(new ApiResponse.AuthorRemoved("another", TestData.author1.id()));
    assertThat(robot.lookup("#authors").queryListView().getItems()).contains(TestData.author1);
    apiBus.emulIn(new ApiResponse.AuthorRemoved("q1", TestData.author1.id()));
    assertThat(robot.lookup("#authors").queryListView().getItems()).doesNotContain(TestData.author1);
  }
    


  @Test @DisplayName("sends 'add inspector' request")
  void addInspectorRequest(FxRobot robot) {
    putStaff();
    uiBus.emulIn(new Quizzes.ShowQuiz(TestData.list.get(0)));
    robot
      .clickOn("#selectedInspector")
        .type(KeyCode.DOWN, 1)
        .type(KeyCode.ENTER)
        .clickOn("#addInspector")
        ;
    assertThat(apiBus.poll()).isEqualTo(new ApiRequest.AddInspector("q1", "author1"));
  }

  @Test @DisplayName("displays new inspector on api event")
  void showNewInspector(FxRobot robot) {
    putStaff();
    uiBus.emulIn(new Quizzes.ShowQuiz(TestData.list.get(0)));

    apiBus.emulIn(new ApiResponse.InspectorAdded("another", TestData.author1.id()));
    assertThat(robot.lookup("#inspectors").queryListView().getItems()).doesNotContain(TestData.author1);

    apiBus.emulIn(new ApiResponse.InspectorAdded("q1", TestData.author1.id()));
    assertThat(robot.lookup("#inspectors").queryListView().getItems()).contains(TestData.author1);
  }

  @Test @DisplayName("sends 'remove inspector' request")
  void removeInspector(FxRobot robot) {
    uiBus.emulIn(new Quizzes.ShowQuiz(TestData.list.get(0)));
    robot.clickOn(robot.lookup("#inspectors")
        .lookup(".list-cell").lookup(LabeledMatchers.hasText("inspector1 name")).queryParent()
          .lookup(".remove-item"))
          ;
    assertThat(apiBus.poll()).isEqualTo(new ApiRequest.RemoveInspector("q1", "inspector1"));
  }

  @Test @DisplayName("removes inspector on api event")
  void showRemoveInspector(FxRobot robot) {
    uiBus.emulIn(new Quizzes.ShowQuiz(TestData.list.get(0)));
    apiBus.emulIn(new ApiResponse.InspectorRemoved("another", TestData.inspector1.id()));
    assertThat(robot.lookup("#inspectors").queryListView().getItems()).contains(TestData.inspector1);
    apiBus.emulIn(new ApiResponse.InspectorRemoved("q1", TestData.inspector1.id()));
    assertThat(robot.lookup("#inspectors").queryListView().getItems()).doesNotContain(TestData.inspector1);
  }

}

