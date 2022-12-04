package author.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class InUpdateQuiz {

    public final String title;
    public final String intro;
    public final int recommendedLength;

    @JsonCreator
    public InUpdateQuiz(
        @JsonProperty("title") String title,
        @JsonProperty("intro") String intro,
        @JsonProperty("recommendedLength") int recommendedLength
    ) {
        this.recommendedLength = recommendedLength;
        this.intro = intro;
        this.title = title;
    }

}
