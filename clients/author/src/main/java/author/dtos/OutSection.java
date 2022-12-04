package author.dtos;

import author.dtos.OutItem;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class OutSection {

    public final String sc;
    public final String title;
    public final String intro;
    public final List<OutItem> items;

    @JsonCreator
    public OutSection(
        @JsonProperty("sc") String sc,
        @JsonProperty("title") String title,
        @JsonProperty("intro") String intro,
        @JsonProperty("items") List<OutItem> items
    ) {
        this.sc = sc;
        this.intro = intro;
        this.title = title;
        this.items = items;
    }

}
