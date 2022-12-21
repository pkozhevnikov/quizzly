package author.panes.quiz;

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

import author.events.ApiResponse;
import author.requests.ApiRequest;
import author.util.*;
import author.dtos.*;
import author.messages.*;

import author.testutil.*;
import author.TestData;

import lombok.val;

@ExtendWith(ApplicationExtension.class)
@DisplayName("Quiz pane")
class QuizPaneTest {

  TestBus<ApiResponse, ApiRequest> apiBus = new TestBus<>();
  TestBus<MainUIMessage, MainUIMessage> uiBus = new TestBus<>();


  @Start
  private void start(Stage stage) throws Exception {
    Node sut = Factories.nodeWith(new QuizPane(apiBus, uiBus));
    stage.setScene(new Scene((Parent) sut, 1000, 700));
    stage.show();
  }

  @SuppressWarnings("unchecked")
  private void checkReadiness(FxRobot robot, OutFullQuiz quiz) {
    val labels = robot.lookup("#readiness").queryAs(VBox.class).getChildren();
    assertThat(labels).allSatisfy(l -> assertThat(l.getStyleClass()).contains("check"));
    val textIs = quiz.readinessSigns().stream().map(a -> 
      (Consumer<Labeled>) l -> assertThat(l.getText()).isEqualTo(a.name()))
      .toArray(Consumer[]::new);
    assertThat(labels).satisfiesExactlyInAnyOrder(textIs);
  }

  @SuppressWarnings("unchecked")
  private void checkApprovals(FxRobot robot, OutFullQuiz quiz) {
    val labels = robot.lookup("#approvals").queryAs(VBox.class).getChildren();
    val sats = Stream.concat(
      quiz.approvals().stream().map(i ->
        (Consumer<Labeled>) l -> {
          assertThat(l.getStyleClass()).contains("check");
          assertThat(l.getText()).isEqualTo(i.name());
        }
      ),
      quiz.disapprovals().stream().map(i ->
        (Consumer<Labeled>) l -> {
          assertThat(l.getStyleClass()).contains("uncheck");
          assertThat(l.getText()).isEqualTo(i.name());
        }
      )
    ).toArray(Consumer[]::new);
    assertThat(labels).satisfiesExactlyInAnyOrder(sats);
  }

  private void putQuizForUser(OutFullQuiz quiz, OutPerson user) {
    uiBus.emulIn(new MainUIMessage.ActingAs(user));
    uiBus.emulIn(new MainUIMessage.SetQuiz(quiz));
  }
    
  @Test @DisplayName("correct visual state on quiz set")
  void quizSet(FxRobot robot) {
    var lu = new Lookup(robot);
    putQuizForUser(TestData.fullQuiz1, TestData.author1);
    assertThat(lu.label("#id")).hasText("q1");
    assertThat(lu.label("#status")).hasText("Composing");
    assertThat(lu.input("#title")).hasText("q1 title");
    assertThat(lu.input("#intro")).hasText("q1 intro");
    assertThat(lu.input("#recommendedLength")).hasText("65");
    assertThat(lu.label("#curatorName")).hasText("curator name");
    assertThat(lu.button("#saveChanges")).isEnabled();
    assertThat(lu.button("#setReady")).isEnabled();
    assertThat(lu.button("#unsetReady")).isDisabled();
    assertThat(lu.button("#approve")).isDisabled();
    assertThat(lu.button("#disapprove")).isDisabled();
    checkReadiness(robot, TestData.fullQuiz1);
    checkApprovals(robot, TestData.fullQuiz1);
    assertThat(lu.table("#sections").getItems()).containsExactly(
      TestData.section1, TestData.section2, TestData.section3);
  } 

  @Test @DisplayName("sends edit request on link click")
  void editClick(FxRobot robot) {
    putQuizForUser(TestData.fullQuiz1, TestData.author1);
    val go = robot.from(robot.lookup("section 2 title").query().getParent()).lookup(".edit-section").query();
    robot.clickOn(go);
    assertThat(uiBus.poll()).isEqualTo(new MainUIMessage.EditSection(TestData.section2));
  }

  @Test @DisplayName("sends section up request on link click")
  void sectionUp(FxRobot robot) {
    putQuizForUser(TestData.fullQuiz1, TestData.author1);
    val go = robot.from(robot.lookup("section 3 title").query().getParent()).lookup(".section-up").query();
    robot.clickOn(go);
    assertThat(apiBus.poll()).isEqualTo(new ApiRequest.MoveSection("q1-3", true));
  }

  @Test @DisplayName("sends section down request on link click")
  void sectionDown(FxRobot robot) throws Exception {
    putQuizForUser(TestData.fullQuiz1, TestData.author1);
    val go = robot.from(robot.lookup("section 1 title").query().getParent()).lookup(".section-down").query();
    robot.clickOn(go);
    assertThat(apiBus.poll()).isEqualTo(new ApiRequest.MoveSection("q1-1", false));
  }

  @Test @DisplayName("sends remove section request on link click")
  void sectionRemove(FxRobot robot) {
    putQuizForUser(TestData.fullQuiz1, TestData.author1);
    val go = robot.from(robot.lookup("section 2 title").query().getParent()).lookup(".remove-section").query();
    robot.clickOn(go);
    assertThat(apiBus.poll()).isEqualTo(new ApiRequest.RemoveSection("q1-2", "q1"));
  }

  private static Stream<Arguments> enableButtons_args() {
    return Stream.of(
      Arguments.of("Composing state, author already signed",
        TestData.fullQuiz1, TestData.author2, true, false, true, false, false),
      Arguments.of("Composing state, author hasn't signed yet",
        TestData.fullQuiz1, TestData.author1, true, true, false, false, false),
      Arguments.of("Composing state, user not on list", 
        TestData.fullQuiz1, TestData.inspector3, false, false, false, false, false),
      Arguments.of("Composing state, user is inspector",
        TestData.fullQuiz1, TestData.inspector1, false, false, false, false, false),
      Arguments.of("Review state, user not on list",
        TestData.fullQuiz1.withState("Review"), TestData.curator,
          false, false, false, false, false),
      Arguments.of("Review state, user is author",
        TestData.fullQuiz1.withState("Review"), TestData.author1,
          false, false, false, false, false),
      Arguments.of("Review state, user is inspector",
        TestData.fullQuiz1.withState("Review"), TestData.inspector1,
          false, false, false, true, true),
      Arguments.of("Released state, user not on list",
        TestData.fullQuiz1.withState("Released"), TestData.author3,
          false, false, false, false, false),
      Arguments.of("Released state, user is author",
        TestData.fullQuiz1.withState("Released"), TestData.author1,
          false, false, false, false, false),
      Arguments.of("Released state, user is inspector",
        TestData.fullQuiz1.withState("Released"), TestData.inspector1,
          false, false, false, false, false)
    );
  }

  @ParameterizedTest(name = "{0}") 
  @DisplayName("buttons enabled/disabled state depends on quiz and current user")
  @MethodSource("enableButtons_args")
  void enabledButtons(String name, OutFullQuiz quiz, OutPerson user, 
      boolean save, boolean setReady, boolean unsetReady, boolean approve, boolean disapprove,
      FxRobot robot) {
    putQuizForUser(quiz, user);
    var lu = new Lookup(robot);
    assertThat(lu.button("#saveChanges").isDisabled()).isNotEqualTo(save);
    assertThat(lu.button("#setReady").isDisabled()).isNotEqualTo(setReady);
    assertThat(lu.button("#unsetReady").isDisabled()).isNotEqualTo(unsetReady);
    assertThat(lu.button("#approve").isDisabled()).isNotEqualTo(approve);
    assertThat(lu.button("#disapprove").isDisabled()).isNotEqualTo(disapprove);

  }

  @Test @DisplayName("sends correct request on save click")
  void saveRequest(FxRobot robot) throws Exception {
    putQuizForUser(TestData.fullQuiz1, TestData.author1);
    val lu = new Lookup(robot);
    robot
      .clickOn(lu.input("#title"))
        .press(KeyCode.CONTROL).type(KeyCode.A).release(KeyCode.CONTROL).write("title plus")
      .clickOn(lu.input("#intro"))
        .press(KeyCode.CONTROL).type(KeyCode.A).release(KeyCode.CONTROL).write("intro plus")
      .clickOn(lu.input("#recommendedLength"))
        .press(KeyCode.CONTROL).type(KeyCode.A).release(KeyCode.CONTROL).write("93")
      .clickOn(lu.button("#saveChanges"))
      ;
    assertThat(apiBus.poll()).isEqualTo(new ApiRequest.UpdateQuiz("title plus", "intro plus", 93));
  }


  private static Stream<Arguments> buttonRequest_args() {
    return Stream.of(
      Arguments.of(TestData.fullQuiz1, "setReady", TestData.author1, new ApiRequest.SetReady("q1")),
      Arguments.of(TestData.fullQuiz1, "unsetReady", TestData.author2, new ApiRequest.UnsetReady("q1")),
      Arguments.of(TestData.fullQuiz1.withState("Review"), "approve", TestData.inspector1, 
            new ApiRequest.Approve("q1")),
      Arguments.of(TestData.fullQuiz1.withState("Review"), "disapprove", TestData.inspector2, 
            new ApiRequest.Disapprove("q1"))
    );
  }
    
  @ParameterizedTest(name = "{1}")
  @DisplayName("sends correct request on button click")
  @MethodSource("buttonRequest_args")
  void setReadyRequest(OutFullQuiz quiz, String buttonId, OutPerson user, 
          ApiRequest expectedRequest, FxRobot robot) {
    putQuizForUser(quiz, user);
    robot.clickOn(robot.lookup("#" + buttonId).queryButton());
    assertThat(apiBus.poll()).isEqualTo(expectedRequest);
  }

  @Test @DisplayName("updates readiness list on set ready")
  void setReady(FxRobot robot) {
    putQuizForUser(TestData.fullQuiz1, TestData.author1);
    apiBus.emulIn(new ApiResponse.ReadySet("q2", TestData.author3.id()));
    checkReadiness(robot, TestData.fullQuiz1);
    apiBus.emulIn(new ApiResponse.ReadySet("q1", TestData.author1.id()));
    val readinessPlus = new HashSet<>(TestData.fullQuiz1.readinessSigns());
    readinessPlus.add(TestData.author1);
    checkReadiness(robot, TestData.fullQuiz1.withReadinessSigns(readinessPlus));
    assertThat(robot.lookup("#setReady").queryButton()).isDisabled();
    assertThat(robot.lookup("#unsetReady").queryButton()).isEnabled();
  }

  @Test @DisplayName("upodates readiness list on unset ready")
  void unsetReady(FxRobot robot) {
    putQuizForUser(TestData.fullQuiz1, TestData.author2);
    apiBus.emulIn(new ApiResponse.ReadyUnset("q2", TestData.author2.id()));
    checkReadiness(robot, TestData.fullQuiz1);
    apiBus.emulIn(new ApiResponse.ReadyUnset("q1", TestData.author2.id()));
    val readinessMinus = new HashSet<>(TestData.fullQuiz1.readinessSigns());
    readinessMinus.remove(TestData.author2);
    checkReadiness(robot, TestData.fullQuiz1.withReadinessSigns(readinessMinus));
    assertThat(robot.lookup("#setReady").queryButton()).isEnabled();
    assertThat(robot.lookup("#unsetReady").queryButton()).isDisabled();
  }

  @Test @DisplayName("updates approvals on approve")
  void approve(FxRobot robot) {
    putQuizForUser(TestData.fullQuiz1, TestData.inspector2);
    apiBus.emulIn(new ApiResponse.Approved("q2", TestData.inspector2.id()));
    checkApprovals(robot, TestData.fullQuiz1);
    apiBus.emulIn(new ApiResponse.Approved("q1", TestData.inspector2.id()));
    val approvals = new HashSet<>(TestData.fullQuiz1.approvals());
    val disapprovals = new HashSet<>(TestData.fullQuiz1.disapprovals());
    approvals.add(TestData.inspector2);
    disapprovals.remove(TestData.inspector2);
    checkApprovals(robot, TestData.fullQuiz1.withApprovals(approvals)
      .withDisapprovals(disapprovals));
  }

  @Test @DisplayName("updates approvals on disapprove")
  void disapprove(FxRobot robot) {
    putQuizForUser(TestData.fullQuiz1, TestData.inspector1);
    apiBus.emulIn(new ApiResponse.Disapproved("q2", TestData.inspector1.id()));
    checkApprovals(robot, TestData.fullQuiz1);
    apiBus.emulIn(new ApiResponse.Disapproved("q1", TestData.inspector1.id()));
    val approvals = new HashSet<>(TestData.fullQuiz1.approvals());
    val disapprovals = new HashSet<>(TestData.fullQuiz1.disapprovals());
    approvals.remove(TestData.inspector1);
    disapprovals.add(TestData.inspector1);
    checkApprovals(robot, TestData.fullQuiz1.withApprovals(approvals)
      .withDisapprovals(disapprovals));
  }
    

  @Test @DisplayName("section button box is show depending on quiz state and current user")
  void sectionButtonBoxVisibility(FxRobot robot) {
    putQuizForUser(TestData.fullQuiz1, TestData.author3);
    assertThat(robot.lookup("#buttonBox").tryQuery()).isEmpty();
    putQuizForUser(TestData.fullQuiz1, TestData.curator);
    assertThat(robot.lookup("#buttonBox").tryQuery()).isEmpty();
    putQuizForUser(TestData.fullQuiz1, TestData.inspector1);
    assertThat(robot.lookup("#buttonBox").tryQuery()).isEmpty();
    putQuizForUser(TestData.fullQuiz1, TestData.author1);
    assertThat(robot.lookup("#buttonBox").tryQuery()).isPresent();

    putQuizForUser(TestData.fullQuiz1.withState("Review"), TestData.author1);
    assertThat(robot.lookup("#buttonBox").tryQuery()).isEmpty();
    putQuizForUser(TestData.fullQuiz1.withState("Review"), TestData.curator);
    assertThat(robot.lookup("#buttonBox").tryQuery()).isEmpty();
    putQuizForUser(TestData.fullQuiz1.withState("Review"), TestData.inspector1);
    assertThat(robot.lookup("#buttonBox").tryQuery()).isEmpty();
    
    putQuizForUser(TestData.fullQuiz1.withState("Released"), TestData.author1);
    assertThat(robot.lookup("#buttonBox").tryQuery()).isEmpty();
    putQuizForUser(TestData.fullQuiz1.withState("Released"), TestData.curator);
    assertThat(robot.lookup("#buttonBox").tryQuery()).isEmpty();
    putQuizForUser(TestData.fullQuiz1.withState("Released"), TestData.inspector1);
    assertThat(robot.lookup("#buttonBox").tryQuery()).isEmpty();
  }

  @Test @DisplayName("shows and hides 'create section'")
  void showCreateSection(FxRobot robot) {
    putQuizForUser(TestData.fullQuiz1, TestData.author1);
    assertThat(robot.lookup("#buttonBox").tryQuery()).isPresent();
    assertThat(robot.lookup("#createSectionForm").tryQuery()).isEmpty();
    robot.clickOn("#newSection");
    assertThat(robot.lookup("#buttonBox").tryQuery()).isEmpty();
    assertThat(robot.lookup("#createSectionForm").tryQuery()).isPresent();
    assertThat(robot.lookup("#newSectionTitle").queryTextInputControl()).hasText("");
    robot.clickOn("#cancelCreateSection");
    assertThat(robot.lookup("#buttonBox").tryQuery()).isPresent();
    assertThat(robot.lookup("#createSectionForm").tryQuery()).isEmpty();
  }

  @Test @DisplayName("sends 'create section' request on button click")
  void createSectionRequest(FxRobot robot) {
    putQuizForUser(TestData.fullQuiz1, TestData.author1);
    robot.clickOn("#newSection");
    robot.clickOn("#newSectionTitle").write("new section title")
      .clickOn("#createSection");
    assertThat(apiBus.poll()).isEqualTo(new ApiRequest.CreateSection("q1", "new section title"));
  }

  @Test @DisplayName("hides create section form on 'section created' response")
  void hideSectionForm(FxRobot robot) {
    putQuizForUser(TestData.fullQuiz1, TestData.author1);
    robot.clickOn("#newSection");
    apiBus.emulIn(new ApiResponse.SectionCreated("q2", "q2-1"));
    assertThat(robot.lookup("#createSectionForm").tryQuery()).isPresent();
    apiBus.emulIn(new ApiResponse.SectionCreated("q1", "q1-1"));
    assertThat(robot.lookup("#createSectionForm").tryQuery()).isEmpty();
  }

}

