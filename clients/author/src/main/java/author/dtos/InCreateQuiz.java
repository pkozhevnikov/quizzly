package author.dtos;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@lombok.Value
@JsonIgnoreProperties({"hashCode", "toString"})
public class InCreateQuiz {

    String id;
    String title;
    String intro;
    int recommendedLength;
    Set<String> authors;
    Set<String> inspectors;


}
