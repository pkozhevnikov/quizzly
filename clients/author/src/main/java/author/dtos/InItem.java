package author.dtos;

import author.dtos.InStatement;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@lombok.Value
@JsonIgnoreProperties({"hashCode", "toString"})
public class InItem {

    String sc;
    String intro;
    InStatement definition;
    List<InStatement> hints;
    boolean hintsVisible;
    List<Integer> solutions;


}
