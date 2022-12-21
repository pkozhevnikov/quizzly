package author;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.*;
import org.testfx.matcher.control.*;
import org.testfx.assertions.api.*;

import javafx.scene.Scene;
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

import author.panes.LoginPane;
import author.events.LoginEvent;
import author.requests.LoginRequest;

@ExtendWith(ApplicationExtension.class)
@DisplayName("Login pane")
class LoginPaneTest {

  private LoginPane sut;

  private PublishSubject<LoginEvent> loginEvents = PublishSubject.create();
  private Queue<LoginRequest> requestsQueue = new LinkedList<>();
  private Consumer<LoginRequest> requests = r -> requestsQueue.offer(r);

  @Start
  private void start(Stage stage) {
    sut = new LoginPane(loginEvents, requests);
    stage.setScene(new Scene(sut));
    stage.show();
  }

  private TestObserver<LoginEvent> pushEvent(LoginEvent e) {
    TestObserver<LoginEvent> testsub = TestObserver.create();
    loginEvents.subscribe(testsub);
    javafx.application.Platform.runLater(() -> loginEvents.onNext(e));
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
  @DisplayName("should behave correctly on login failure event")
  void wrongCreds(FxRobot robot) throws Exception {
    Lookup lookup = new Lookup(robot);
    assertThat(lookup.label("#message").getText()).isEmpty();
    pushEvent(new LoginEvent.Failure(""));
    assertThat(lookup.input("#password").getText()).isNull();
    assertThat(lookup.button("#button")).isEnabled();
    assertThat(lookup.label("#message"))
      .hasText("Wrong username or password")
      .hasStyle("-fx-text-fill:red");
  }
    
  @Test
  @DisplayName("should behave correctly on login success event")
  void successLogin(FxRobot robot) {
    Labeled message = robot.lookup("#message").queryLabeled();
    assertThat(message.getText()).isEmpty();
    pushEvent(new LoginEvent.Success("", TestData.author1));
    assertThat(message)
      .hasText("Logged in successfully")
      .hasStyle("-fx-text-fill:green");
  }

  @Test
  @DisplayName("should behave correctly on button click")
  void disableButton(FxRobot robot) {
    robot
      .clickOn("#username").write("somename")
      .clickOn("#password").write("somepass")
      .clickOn("#button")
      ;
    Lookup lu = new Lookup(robot);
    assertThat(lu.button("#button")).isDisabled();
    assertThat(lu.input("#password").getText()).isNull();
    assertThat(lu.label("#message").getText()).isNull();
    assertThat(requestsQueue.poll()).isEqualTo(new LoginRequest.Login("somename", "somepass"));
  }

}
    
