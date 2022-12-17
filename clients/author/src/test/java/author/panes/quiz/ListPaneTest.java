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
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.observers.TestObserver;

import java.util.function.Consumer;
import java.util.LinkedList;
import java.util.Queue;

import author.panes.quiz.ListPane;
import author.events.ApiResponse;
import author.requests.ApiRequest;

import author.testutil.*;
import author.TestData;

@ExtendWith(ApplicationExtension.class)
@DisplayName("List pane")
class ListPaneTest {

  TestBus<ApiResponse, ApiRequest> apiBus = new TestBus<>();

  @Start
  private void start(Stage stage) throws Exception {
    Node sut = ListPane.create(apiBus);
    stage.setScene(new Scene((Parent) sut, 1000, 700));
    stage.show();
  }

  @Test @DisplayName("displays quiz list")
  void displayList(FxRobot robot) {
    Lookup lu = new Lookup(robot);
    apiBus.emulIn(new ApiResponse.QuizList(TestData.list));
    assertThat(lu.table("#list"))
      .hasExactlyNumRows(3)
      .containsRow("q1", "q1 title", "Composing")
      .containsRow("q2", "q2 title", "Review")
      .containsRow("q3", "q3 title", "Released")
      ;
  }

  @Test @DisplayName("sends show quiz message on quiz selection")
  void showQuiz(FxRobot robot) throws Exception {
    Lookup lu = new Lookup(robot);
    apiBus.emulIn(new ApiResponse.QuizList(TestData.list));
    robot.clickOn(TableViewMatchers.hasTableCell("q3"));
    //assertThat(uiBus.poll()).sameAs(new ListPane.ShowQuiz(TestData.list.get(2)));
  }
    
}
