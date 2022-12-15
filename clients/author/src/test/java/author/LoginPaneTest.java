package author;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.*;
import org.testfx.matcher.control.*;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import static org.mockito.Mockito.*;
import static org.testfx.api.FxAssert.*;
import static org.hamcrest.Matchers.*;
import static org.testfx.assertions.api.Assertions.*;

import author.panes.LoginPane;

@ExtendWith(ApplicationExtension.class)
@DisplayName("On login pane")
class LoginPaneTest {

  private LoginPane sut;

  @Start
  private void start(Stage stage) {
    sut = new LoginPane();
    stage.setScene(new Scene(sut));
    stage.show();
  }

  @Test
  @DisplayName("display message if not logged in")
  void login(FxRobot robot) {
    Auth auth = mock(Auth.class);
    when(auth.login(anyString(), anyString())).thenReturn(false);
    sut.setAuth(auth);
    
    robot
      .clickOn("#username").write("somename")
      .clickOn("#password").write("somepass")
      .clickOn("#button")
      ;
    assertThat(robot.lookup("#message").queryLabeled()).hasText("Wrong username or password");
    assertThat(robot.lookup("#password").queryTextInputControl()).hasText(nullValue(String.class));

    verify(auth).login("somename", "somepass");
  }

}
    
