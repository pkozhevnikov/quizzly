package author.dtos;

import author.dtos.OutPerson;
import author.dtos.OutSection;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Set;

public class OutQuiz {

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

    @JsonCreator
    public OutQuiz(
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
        @JsonProperty("sections") List<OutSection> sections
    ) {
        this.recommendedLength = recommendedLength;
        this.curator = curator;
        this.disapprovals = disapprovals;
        this.intro = intro;
        this.inspectors = inspectors;
        this.approvals = approvals;
        this.obsolete = obsolete;
        this.readinessSigns = readinessSigns;
        this.id = id;
        this.title = title;
        this.sections = sections;
        this.authors = authors;
    }

}
