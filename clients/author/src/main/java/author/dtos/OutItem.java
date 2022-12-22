package author.dtos;

import author.dtos.OutStatement;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@lombok.Value
@JsonIgnoreProperties({"hashCode", "toString"})
public class OutItem {

    String sc;
    String intro;
    OutStatement definition;
    List<OutStatement> hints;
    boolean hintsVisible;
    List<Integer> solutions;


}
