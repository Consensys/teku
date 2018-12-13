/*
 * Copyright 2018 ConsenSys AG.
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

package net.consensys.artemis.util.bytes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class Bytes3Test {

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenWrappingArraySmallerThan3() {
    Bytes3.wrap(new byte[2]);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenWrappingArrayLargerThan3() {
    Bytes3.wrap(new byte[4]);
  }

  @Test
  public void leftPadAValueToBytes3() {
    Bytes3 b3 = Bytes3.leftPad(BytesValue.of(1, 2, 3));
    assertThat(b3.size()).isEqualTo(3);
    assertThat(b3.get(0)).isEqualTo((byte) 1);
    assertThat(b3.get(1)).isEqualTo((byte) 2);
    assertThat(b3.get(2)).isEqualTo((byte) 3);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenLeftPaddingValueLargerThan3() {
    Bytes3.leftPad(MutableBytesValue.create(4));
  }
}
