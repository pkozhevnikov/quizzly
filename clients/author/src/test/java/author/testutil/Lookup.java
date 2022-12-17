package author.testutil;

import javafx.scene.control.*;
import org.testfx.api.FxRobot;

public class Lookup {
  private FxRobot robot;
  public Lookup(FxRobot robot) { this. robot = robot; }
  public Labeled label(String id) { return robot.lookup(id).queryLabeled(); }
  public TextInputControl input(String id) { return robot.lookup(id).queryTextInputControl(); }
  public Button button(String id) { return robot.lookup(id).queryButton(); }
  public TableView<?> table(String id) { return robot.lookup(id).queryTableView(); }
  public ComboBox<?> combo(String id) { return robot.lookup(id).queryComboBox(); }
  public ListView<?> list(String id) { return robot.lookup(id).queryListView(); }
}

