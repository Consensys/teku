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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;

class SSZVectorTest {
  @Test
  void initTest() {
    SSZVector<Bytes32> vector = SSZVector.createMutable(10, Bytes32.ZERO);
    assertEquals(vector.get(0), Bytes32.ZERO);
    assertEquals(vector.get(9), Bytes32.ZERO);
  }
}
