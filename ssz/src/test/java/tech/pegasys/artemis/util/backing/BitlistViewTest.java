/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.artemis.util.backing;

import org.assertj.core.api.Assertions;
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
      Bitlist bitlist1 = ViewUtils.getBitvector(bitlistView);

      Assertions.assertThat(bitlist1).isEqualTo(bitlist);
    }
  }
}
