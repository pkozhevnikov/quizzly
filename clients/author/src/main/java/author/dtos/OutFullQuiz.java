package author.dtos;

import author.dtos.OutPerson;
import author.dtos.OutSection;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@lombok.Value
@JsonIgnoreProperties({"hashCode", "toString"})
public class OutFullQuiz {

    @lombok.With
    String id;
    @lombok.With
    String title;
    @lombok.With
    String intro;
    OutPerson curator;
    @lombok.With
    Set<OutPerson> authors;
    @lombok.With
    Set<OutPerson> inspectors;
    @lombok.With
    Integer recommendedLength;
    @lombok.With
    Set<OutPerson> readinessSigns;
    @lombok.With
    Set<OutPerson> approvals;
    @lombok.With
    Set<OutPerson> disapprovals;
    Boolean obsolete;
    List<OutSection> sections;
    @lombok.With
    String state;


}
