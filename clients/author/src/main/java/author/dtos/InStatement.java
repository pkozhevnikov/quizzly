package author.dtos;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@lombok.Value
@JsonIgnoreProperties({"hashCode", "toString"})
public class InStatement {

    String text;
    String image;


}
