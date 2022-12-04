package author.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OutStatement {

    public final String text;
    public final String image;

    @JsonCreator
    public OutStatement(
        @JsonProperty("text") String text,
        @JsonProperty("image") String image
    ) {
        this.image = image;
        this.text = text;
    }

}
