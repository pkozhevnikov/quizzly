package author.dtos;

import author.dtos.OutPerson;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@lombok.Value
@JsonIgnoreProperties({"hashCode", "toString"})
public class OutCreateDetails {

    Set<OutPerson> authors;
    Set<OutPerson> inspectors;


}
