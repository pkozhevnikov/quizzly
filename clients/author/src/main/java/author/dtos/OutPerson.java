package author.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OutPerson {

    public final String name;
    public final String id;

    @JsonCreator
    public OutPerson(
        @JsonProperty("name") String name,
        @JsonProperty("id") String id
    ) {
        this.name = name;
        this.id = id;
    }

}
