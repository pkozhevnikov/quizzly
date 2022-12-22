package author.dtos;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@lombok.Value
@JsonIgnoreProperties({"hashCode", "toString"})
public class OutStatement {

    String text;
    String image;


}
