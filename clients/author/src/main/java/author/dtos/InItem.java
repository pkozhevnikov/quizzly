package author.dtos;

import author.dtos.InStatement;
import java.util.List;

@lombok.Value
public class InItem {

    String sc;
    String intro;
    InStatement definition;
    List<InStatement> hints;
    boolean hintsVisible;
    List<Integer> solutions;


}
