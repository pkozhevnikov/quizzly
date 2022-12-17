package author.dtos;

import author.dtos.OutPerson;
import java.util.Set;

@lombok.Value
public class OutQuizListed {

    String id;
    String title;
    Boolean obsolete;
    OutPerson curator;
    Set<OutPerson> authors;
    Set<OutPerson> inspectors;
    String state;


}
