package author;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.*;
import org.testfx.matcher.control.*;
import org.testfx.assertions.api.*;

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

import author.panes.ListPane;
import author.events.ApiResponse;
import author.requests.ApiRequest;

@ExtendWith(ApplicationExtension.class)
@DisplayName("List pane")
class ListPaneTest {

  private PublishSubject<ApiResponse> responses = PublishSubject.create();
  private Queue<ApiRequest> requestsQueue = new LinkedList<>();
  private Consumer<ApiRequest> requests = r -> requestsQueue.offer(r);

  @Start
  private void start(Stage stage) throws Exception {
    Node sut = ListPane.create(responses, requests);
    stage.setScene(new Scene((Parent) sut, 1000, 700));
    stage.show();
  }

  private TestObserver<ApiResponse> pushEvent(ApiResponse e) {
    TestObserver<ApiResponse> testsub = TestObserver.create();
    responses.subscribe(testsub);
    javafx.application.Platform.runLater(() -> responses.onNext(e));
    testsub.awaitCount(1);
    testsub.dispose();
    return testsub;
  }

  private static class Lookup {
    private FxRobot robot;
    Lookup(FxRobot robot) { this. robot = robot; }
    Labeled label(String id) { return robot.lookup(id).queryLabeled(); }
    TextInputControl input(String id) { return robot.lookup(id).queryTextInputControl(); }
    Button button(String id) { return robot.lookup(id).queryButton(); }
  }

  @Test
  @DisplayName("initialized in correct state")
  void initialized(FxRobot robot) {
    Lookup lu = new Lookup(robot);

    assertThat(lu.input("#newTitle")).isDisabled();
    assertThat(lu.input("#newId")).isDisabled();
    assertThat(lu.button("#newSave")).isDisabled();
    assertThat(lu.button("#newAddAuthor")).isDisabled();
    assertThat(lu.button("#newAddInspector")).isDisabled();

    assertThat(lu.button("#addAuthor")).isDisabled();
    assertThat(lu.button("#addInspector")).isDisabled();
    assertThat(lu.button("#setObsolete")).isDisabled();

  }

  @Test
  @DisplayName("displays lists on response")
  void displayList(FxRobot robot) {
    Lookup lu = new Lookup(robot);
    pushEvent(new ApiResponse.QuizList(TestData.list));
  }
    
}
