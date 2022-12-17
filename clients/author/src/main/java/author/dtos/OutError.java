package author.dtos;

import author.dtos.OutErrorReason;
import java.util.List;

@lombok.Value
public class OutError {

    OutErrorReason reason;
    List<String> clues;


}
