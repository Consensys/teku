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

package tech.pegasys.teku.ssz.backing;

import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;
import tech.pegasys.teku.ssz.backing.view.SszUtils;

public class BitlistViewTest {

  @Test
  public void basicTest() {
    for (int size : new int[] {100, 255, 256, 300, 1000, 1023}) {
      int[] bitIndexes =
          IntStream.concat(IntStream.range(0, size).filter(i -> i % 2 == 0), IntStream.of(0))
              .toArray();
      Bitlist bitlist = new Bitlist(size, size, bitIndexes);

      SszList<SszBit> bitlistView = SszUtils.toSszBitList(bitlist);
      Bitlist bitlist1 = SszUtils.getBitlist(bitlistView);

      Assertions.assertThat(bitlist1).isEqualTo(bitlist);
    }
  }

  @Disabled("the Tuweni Bytes issue: https://github.com/apache/incubator-tuweni/issues/186")
  @Test
  public void tuweniBytesIssue() {
    Bytes slicedBytes = Bytes.wrap(Bytes.wrap(new byte[32]), Bytes.wrap(new byte[6])).slice(0, 37);

    Assertions.assertThatCode(slicedBytes::copy).doesNotThrowAnyException();

    Bytes wrappedBytes = Bytes.wrap(slicedBytes, Bytes.wrap(new byte[1]));

    Assertions.assertThatCode(wrappedBytes::toArrayUnsafe).doesNotThrowAnyException();
    Assertions.assertThatCode(wrappedBytes::toArray).doesNotThrowAnyException();
    Assertions.assertThatCode(() -> Bytes.concatenate(slicedBytes, Bytes.wrap(new byte[1])))
        .doesNotThrowAnyException();
  }
}
