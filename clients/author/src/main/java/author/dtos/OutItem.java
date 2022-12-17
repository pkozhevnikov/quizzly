package author.dtos;

import author.dtos.OutStatement;
import java.util.List;

@lombok.Value
public class OutItem {

    String sc;
    String intro;
    OutStatement definition;
    List<OutStatement> hints;
    boolean hintsVisible;
    List<Integer> solutions;


}
