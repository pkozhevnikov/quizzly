package author.dtos;

import author.dtos.OutPerson;
import author.dtos.OutQuizState;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

public class OutQuizListed {

    public final String id;
    public final String title;
    public final Boolean obsolete;
    public final OutPerson curator;
    public final Set<OutPerson> authors;
    public final Set<OutPerson> inspectors;
    public final String state;

    @JsonCreator
    public OutQuizListed(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("obsolete") Boolean obsolete,
        @JsonProperty("curator") OutPerson curator,
        @JsonProperty("authors") Set<OutPerson> authors,
        @JsonProperty("inspectors") Set<OutPerson> inspectors,
        @JsonProperty("state") String state
    ) {
        this.curator = curator;
        this.inspectors = inspectors;
        this.obsolete = obsolete;
        this.id = id;
        this.state = state;
        this.title = title;
        this.authors = authors;
    }

}
