package author.dtos;

import author.dtos.OutPerson;
import author.dtos.OutQuizState;
import author.dtos.OutSection;
import java.util.List;
import java.util.Set;

@lombok.Value
public class OutFullQuiz {

    String id;
    String title;
    String intro;
    OutPerson curator;
    Set<OutPerson> authors;
    Set<OutPerson> inspectors;
    Integer recommendedLength;
    Set<OutPerson> readinessSigns;
    Set<OutPerson> approvals;
    Set<OutPerson> disapprovals;
    Boolean obsolete;
    List<OutSection> sections;
    OutQuizState state;


}
