package author;

import author.dtos.*;

import java.util.List;
import java.util.Set;

public class TestData {

  public static OutPerson curator = new OutPerson("curator name", "curator");
  public static OutPerson author1 = new OutPerson("author1 name", "author1");
  public static OutPerson author2 = new OutPerson("author2 name", "author2");
  public static OutPerson inspector1 = new OutPerson("inspector1 name", "inspector1");
  public static OutPerson inspector2 = new OutPerson("inspector2 name", "inspector2");

  public static List<OutQuizListed> list = List.of(
    new OutQuizListed("q1", "q1 title", false, curator,
      Set.of(author1, author2), Set.of(inspector1, inspector2), "Composing"), 
    new OutQuizListed("q2", "q2 title", false, author1,
      Set.of(curator, author2), Set.of(inspector1, inspector2), "Review"), 
    new OutQuizListed("q3", "q3 title", true, inspector1,
      Set.of(author1, author2), Set.of(curator, inspector2), "Released")
  );
  
  public static OutQuizListed newQuiz = new OutQuizListed("q4", "q4 title", false, author2,
    Set.of(curator, author1), Set.of(inspector1, inspector2), "Composing");
}
