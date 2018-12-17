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

package tech.pegasys.artemis.util.bytes;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.util.bytes.Bytes48.intToBytes48;

import org.junit.Test;

public class Bytes48Test {

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenWrappingArraySmallerThan48() {
    Bytes48.wrap(new byte[47]);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenWrappingArrayLargerThan48() {
    Bytes48.wrap(new byte[49]);
  }

  @Test
  public void convertIntToBytes48() {
    Bytes48 expected = Bytes48.wrap(new byte[]
        {(byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0,
            (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0,
            (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0,
            (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0,
            (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0,
            (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 20, (byte) 103, (byte) -62, (byte) 41});
    Bytes48 actual =
        intToBytes48(342344233);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void leftPadAValueToBytes48() {
    Bytes48 b48 = Bytes48.leftPad(BytesValue.of(1, 2, 3));
    assertThat(b48.size()).isEqualTo(48);
    for (int i = 0; i < 44; ++i) {
      assertThat(b48.get(i)).isEqualTo((byte) 0);
    }
    assertThat(b48.get(45)).isEqualTo((byte) 1);
    assertThat(b48.get(46)).isEqualTo((byte) 2);
    assertThat(b48.get(47)).isEqualTo((byte) 3);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenLeftPaddingValueLargerThan48() {
    Bytes48.leftPad(MutableBytesValue.create(49));
  }
}
