package tech.pegasys.artemis.util.backing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.backing.view.BasicViews.BitView;
import tech.pegasys.artemis.util.backing.view.ViewUtils;

public class BitlistViewTest {

  @Test
  public void basicTest() {
    for (int size : new int[] {100, 255, 256, 300, 1000, 1023}) {
      Bitlist bitlist = new Bitlist(size, size);
      for (int i = 0; i < size; i++) {
        if (i % 2 == 0) {
          bitlist.setBit(i);
        }
      }
      bitlist.setBit(0);

      ListViewRead<BitView> bitlistView = ViewUtils.createBitlistView(bitlist);
      Bitlist bitlist1 = ViewUtils.getBitlist(bitlistView);

      Assertions.assertEquals(bitlist, bitlist1);
    }
  }

}
