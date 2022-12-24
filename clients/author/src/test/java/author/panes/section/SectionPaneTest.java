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

  @Test @DisplayName("sets data correctly")
  void setData(FxRobot robot) throws Exception {
    uiBus.emulIn(new MainUIMessage.EditSection(TestData.section1));
    assertThat(robot.lookup("#title").queryTextInputControl()).hasText("section 1 title");
    assertThat(robot.lookup("#intro").queryTextInputControl()).hasText("section 1 intro");
  }

}
