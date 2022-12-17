package author.dtos;

import java.util.Set;

@lombok.Value
public class InCreateQuiz {

    String id;
    String title;
    String intro;
    int recommendedLength;
    Set<String> authors;
    Set<String> inspectors;


}
