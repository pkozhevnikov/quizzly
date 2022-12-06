package author.dtos;

import author.dtos.OutPerson;
import author.dtos.OutQuizState;
import author.dtos.OutSection;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Set;

public class OutFullQuiz {

    public final String id;
    public final String title;
    public final String intro;
    public final OutPerson curator;
    public final Set<OutPerson> authors;
    public final Set<OutPerson> inspectors;
    public final Integer recommendedLength;
    public final Set<OutPerson> readinessSigns;
    public final Set<OutPerson> approvals;
    public final Set<OutPerson> disapprovals;
    public final Boolean obsolete;
    public final List<OutSection> sections;
    public final OutQuizState state;

    @JsonCreator
    public OutFullQuiz(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("intro") String intro,
        @JsonProperty("curator") OutPerson curator,
        @JsonProperty("authors") Set<OutPerson> authors,
        @JsonProperty("inspectors") Set<OutPerson> inspectors,
        @JsonProperty("recommendedLength") Integer recommendedLength,
        @JsonProperty("readinessSigns") Set<OutPerson> readinessSigns,
        @JsonProperty("approvals") Set<OutPerson> approvals,
        @JsonProperty("disapprovals") Set<OutPerson> disapprovals,
        @JsonProperty("obsolete") Boolean obsolete,
        @JsonProperty("sections") List<OutSection> sections,
        @JsonProperty("state") OutQuizState state
    ) {
        this.recommendedLength = recommendedLength;
        this.obsolete = obsolete;
        this.readinessSigns = readinessSigns;
        this.title = title;
        this.sections = sections;
        this.curator = curator;
        this.disapprovals = disapprovals;
        this.intro = intro;
        this.inspectors = inspectors;
        this.approvals = approvals;
        this.id = id;
        this.state = state;
        this.authors = authors;
    }

}
