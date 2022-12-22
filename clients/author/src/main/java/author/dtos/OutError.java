package author.dtos;

import author.dtos.OutErrorReason;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@lombok.Value
@JsonIgnoreProperties({"hashCode", "toString"})
public class OutError {

    OutErrorReason reason;
    List<String> clues;


}
