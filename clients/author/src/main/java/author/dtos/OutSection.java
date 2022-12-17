package author.dtos;

import author.dtos.OutItem;
import java.util.List;

@lombok.Value
public class OutSection {

    String sc;
    String title;
    String intro;
    List<OutItem> items;


}
