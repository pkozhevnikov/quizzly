package author;

import author.dtos.*;

import java.util.List;
import java.util.Set;

public class TestData {

  public static OutPerson curator = new OutPerson("curator name", "curator");
  public static OutPerson author1 = new OutPerson("author1 name", "author1");
  public static OutPerson author2 = new OutPerson("author2 name", "author2");
  public static OutPerson author3 = new OutPerson("author3 name", "author3");
  public static OutPerson inspector1 = new OutPerson("inspector1 name", "inspector1");
  public static OutPerson inspector2 = new OutPerson("inspector2 name", "inspector2");
  public static OutPerson inspector3 = new OutPerson("inspector3 name", "inspector3");

  public static List<OutQuizListed> list = List.of(
    new OutQuizListed("q1", "q1 title", false, curator,
      Set.of(author1, author2), Set.of(inspector1, inspector2), "Composing"), 
    new OutQuizListed("q2", "q2 title", false, author1,
      Set.of(curator, author2), Set.of(inspector1, inspector2), "Review"), 
    new OutQuizListed("q3", "q3 title", true, inspector1,
      Set.of(author1, author2), Set.of(curator, inspector2), "Released"),
    new OutQuizListed("q4", "q4 title", false, curator,
      Set.of(author1, author2), Set.of(inspector1, inspector2), "Released")
  );
  
  public static OutQuizListed newQuiz = new OutQuizListed("q5", "q5 title", false, author2,
    Set.of(curator, author1), Set.of(inspector1, inspector2), "Composing");

  public static OutItem item1 = new OutItem("1", "item 1 intro", 
    new OutStatement("item 1 definition", null), List.of(
      new OutStatement("item 1 hint 1", null),
      new OutStatement("item 1 hint 2", null),
      new OutStatement("item 1 hint 3", null)
    ), false, List.of(0, 2));
  public static OutItem item2 = new OutItem("2", "item 2 intro", 
    new OutStatement("item 2 definition", null), List.of(
      new OutStatement("item 2 hint 1", null),
      new OutStatement("item 2 hint 2", null),
      new OutStatement("item 2 hint 3", null)
    ), true, List.of(2));
  public static OutItem item3 = new OutItem("3", "item 3 intro", 
    new OutStatement("item 3 definition", null), List.of(
      new OutStatement("item 3 hint 1", null),
      new OutStatement("item 3 hint 2", null),
      new OutStatement("item 3 hint 3", null)
    ), false, List.of(1));



  public static OutSection section1 = new OutSection("q1-1", "section 1 title", "section 1 intro",
    List.of(item1, item2, item3));
  public static OutSection section2 = new OutSection("q1-2", "section 2 title", "section 2 intro",
    List.of());
  public static OutSection section3 = new OutSection("q1-3", "section 3 title", "section 3 intro",
    List.of());

  public static OutFullQuiz fullQuiz1 = new OutFullQuiz(
    "q1", "q1 title", "q1 intro", curator, Set.of(author1, author2), Set.of(inspector1, inspector2, inspector3),
    65, Set.of(author2), Set.of(inspector1), Set.of(inspector2), false, List.of(section1, section2, section3),
    "Composing"
  );
}
