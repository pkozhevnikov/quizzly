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

import author.testutil.*;

import author.panes.LoginPane;
import author.events.LoginEvent;
import author.requests.LoginRequest;

import lombok.val;

@ExtendWith(ApplicationExtension.class)
@DisplayName("Login pane")
class LoginPaneTest {

  private TestBus<LoginEvent, LoginRequest> loginBus = new TestBus<>();

  @Start
  private void start(Stage stage) {
    val sut = new LoginPane(loginBus);
    stage.setScene(new Scene(sut));
    stage.show();
  }

  @Test
  @DisplayName("should behave correctly on login failure event")
  void wrongCreds(FxRobot robot) throws Exception {
    Lookup lookup = new Lookup(robot);
    assertThat(lookup.label("#message").getText()).isEmpty();
    loginBus.emulIn(new LoginEvent.Failure(""));
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
    loginBus.emulIn(new LoginEvent.Success("", TestData.author1));
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
    assertThat(loginBus.poll()).isEqualTo(new LoginRequest.Login("somename", "somepass"));
  }

}
    
