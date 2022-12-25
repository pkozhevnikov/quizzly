package author.panes;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.params.*;
import org.testfx.api.FxRobot;
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
import org.assertj.core.api.*;

import author.events.*;
import author.requests.*;
import author.util.*;
import author.dtos.*;
import author.messages.*;

import author.testutil.*;
import author.TestData;

import lombok.val;

@ExtendWith(ApplicationExtension.class)
@DisplayName("Root pane")
class RootPaneTest {

  TestBus<ApiResponse, ApiRequest> apiBus = new TestBus<>();
  TestBus<LoginEvent, LoginRequest> loginBus = new TestBus<>();


  @Start
  private void start(Stage stage) throws Exception {
    Node sut = new RootPane(apiBus, loginBus);
    stage.setScene(new Scene((Parent) sut, 1000, 700));
    stage.show();
  }

  @Test @DisplayName("login pane on init")
  void init(FxRobot robot) {
    assertThat(robot.lookup("#mainPane").tryQuery()).isEmpty();
    assertThat(robot.lookup("#loginPane").tryQuery()).isPresent();
  }

  @Test @DisplayName("goes to main pane on login and returns to login pane on logout")
  void switchPanes(FxRobot robot) {
    loginBus.emulIn(new LoginEvent.Success("author1", TestData.author1));
    assertThat(robot.lookup("#mainPane").tryQuery()).isPresent();
    assertThat(robot.lookup("#loginPane").tryQuery()).isEmpty();
    loginBus.emulIn(new LoginEvent.LoggedOut("author1", TestData.author1));
    assertThat(robot.lookup("#mainPane").tryQuery()).isEmpty();
    assertThat(robot.lookup("#loginPane").tryQuery()).isPresent();
  }

}
