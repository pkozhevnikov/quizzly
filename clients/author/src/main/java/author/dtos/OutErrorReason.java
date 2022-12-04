package author.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OutErrorReason {

    public final int code;
    public final String phrase;

    @JsonCreator
    public OutErrorReason(
        @JsonProperty("code") int code,
        @JsonProperty("phrase") String phrase
    ) {
        this.code = code;
        this.phrase = phrase;
    }

}
