package author.dtos;

import author.dtos.OutPerson;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

public class OutCreateDetails {

    public final Set<OutPerson> authors;
    public final Set<OutPerson> inspectors;

    @JsonCreator
    public OutCreateDetails(
        @JsonProperty("authors") Set<OutPerson> authors,
        @JsonProperty("inspectors") Set<OutPerson> inspectors
    ) {
        this.inspectors = inspectors;
        this.authors = authors;
    }

}
