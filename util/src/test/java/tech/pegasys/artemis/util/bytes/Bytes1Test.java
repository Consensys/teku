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
import static tech.pegasys.artemis.util.bytes.Bytes1.intToBytes1;

import org.junit.Test;

public class Bytes1Test {

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenWrappingArraySmallerThan1() {
    Bytes1.wrap(new byte[0]);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenWrappingArrayLargerThan1() {
    Bytes1.wrap(new byte[2]);
  }

  @Test
  public void convertIntToBytes1() {
    Bytes1 expected = Bytes1.wrap(new byte[]{(byte) 1});
    Bytes1 actual = intToBytes1(1);
    assertThat(actual).isEqualTo(expected);
  }

}
