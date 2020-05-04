/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.ssz.ssztypes;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;

class SSZListTest {

  @Test
  void add1Test() {
    SSZMutableList<Bytes32> list = SSZList.createMutable(Bytes32.class, 10);

    Bytes32 randomBytes32 = Bytes32.random();
    list.add(randomBytes32);

    assertTrue(randomBytes32.equals(list.get(0)));
  }

  @Test
  void limitTest() {
    int maxSize = 10;
    SSZMutableList<Bytes32> list = SSZList.createMutable(Bytes32.class, maxSize);

    for (int i = 0; i < maxSize; i++) {
      list.add(Bytes32.random());
    }

    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> {
          list.add(Bytes32.random());
        });
  }
}
