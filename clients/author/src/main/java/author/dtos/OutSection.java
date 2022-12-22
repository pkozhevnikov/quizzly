package author.dtos;

import author.dtos.OutItem;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@lombok.Value
@JsonIgnoreProperties({"hashCode", "toString"})
public class OutSection {

    String sc;
    String title;
    String intro;
    List<OutItem> items;


}
