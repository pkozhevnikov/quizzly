package author.dtos;

import author.dtos.OutPerson;
import java.util.Set;

@lombok.Value
public class OutCreateDetails {

    Set<OutPerson> authors;
    Set<OutPerson> inspectors;


}
