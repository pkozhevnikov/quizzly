package author.dtos;

import author.dtos.OutStatement;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class OutItem {

    public final String sc;
    public final String intro;
    public final OutStatement definition;
    public final List<OutStatement> hints;
    public final boolean hintsVisible;
    public final List<Integer> solutions;

    @JsonCreator
    public OutItem(
        @JsonProperty("sc") String sc,
        @JsonProperty("intro") String intro,
        @JsonProperty("definition") OutStatement definition,
        @JsonProperty("hints") List<OutStatement> hints,
        @JsonProperty("hintsVisible") boolean hintsVisible,
        @JsonProperty("solutions") List<Integer> solutions
    ) {
        this.sc = sc;
        this.hints = hints;
        this.solutions = solutions;
        this.intro = intro;
        this.definition = definition;
        this.hintsVisible = hintsVisible;
    }

}
