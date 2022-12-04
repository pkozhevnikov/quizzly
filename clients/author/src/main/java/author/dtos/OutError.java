package author.dtos;

import author.dtos.OutErrorReason;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class OutError {

    public final OutErrorReason reason;
    public final List<String> clues;

    @JsonCreator
    public OutError(
        @JsonProperty("reason") OutErrorReason reason,
        @JsonProperty("clues") List<String> clues
    ) {
        this.reason = reason;
        this.clues = clues;
    }

}
