package author.dtos;

import author.dtos.OutPerson;
import java.util.Set;

@lombok.Value
public class OutQuizListed {

    String id;
    String title;
    @lombok.With
    Boolean obsolete;
    OutPerson curator;
    @lombok.With
    Set<OutPerson> authors;
    @lombok.With
    Set<OutPerson> inspectors;
    String state;


}
