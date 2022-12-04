package author.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class InUpdateSection {

    public final String title;
    public final String intro;

    @JsonCreator
    public InUpdateSection(
        @JsonProperty("title") String title,
        @JsonProperty("intro") String intro
    ) {
        this.intro = intro;
        this.title = title;
    }

}
