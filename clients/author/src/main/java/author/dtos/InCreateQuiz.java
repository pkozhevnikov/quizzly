package author.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

public class InCreateQuiz {

    public final String id;
    public final String title;
    public final String intro;
    public final int recommendedLength;
    public final Set<String> authors;
    public final Set<String> inspectors;

    @JsonCreator
    public InCreateQuiz(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("intro") String intro,
        @JsonProperty("recommendedLength") int recommendedLength,
        @JsonProperty("authors") Set<String> authors,
        @JsonProperty("inspectors") Set<String> inspectors
    ) {
        this.recommendedLength = recommendedLength;
        this.intro = intro;
        this.inspectors = inspectors;
        this.id = id;
        this.title = title;
        this.authors = authors;
    }

}
