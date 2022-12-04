package author.dtos;

import author.dtos.InStatement;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class InItem {

    public final String sc;
    public final String intro;
    public final InStatement definition;
    public final List<InStatement> hints;
    public final boolean hintsVisible;
    public final List<Integer> solutions;

    @JsonCreator
    public InItem(
        @JsonProperty("sc") String sc,
        @JsonProperty("intro") String intro,
        @JsonProperty("definition") InStatement definition,
        @JsonProperty("hints") List<InStatement> hints,
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
