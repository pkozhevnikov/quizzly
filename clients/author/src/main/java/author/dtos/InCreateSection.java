package author.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class InCreateSection {

    public final String title;

    @JsonCreator
    public InCreateSection(
        @JsonProperty("title") String title
    ) {
        this.title = title;
    }

}
