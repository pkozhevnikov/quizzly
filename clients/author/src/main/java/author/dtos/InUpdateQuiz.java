package author.dtos;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@lombok.Value
@JsonIgnoreProperties({"hashCode", "toString"})
public class InUpdateQuiz {

    String title;
    String intro;
    int recommendedLength;


}
