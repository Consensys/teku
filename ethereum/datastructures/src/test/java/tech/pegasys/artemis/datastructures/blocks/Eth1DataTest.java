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

package tech.pegasys.artemis.datastructures.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.google.common.primitives.UnsignedLong;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;

class Eth1DataTest {

  private Random random = new Random(100);
  private Bytes32 depositRoot = Bytes32.random(random);
  private Bytes32 blockHash = Bytes32.random(random);
  private UnsignedLong depositCount = UnsignedLong.valueOf(100);

  private Eth1Data eth1Data = new Eth1Data(depositRoot, depositCount, blockHash);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    Eth1Data testEth1Data = eth1Data;

    assertEquals(eth1Data, testEth1Data);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    Eth1Data testEth1Data = new Eth1Data(depositRoot, depositCount, blockHash);

    assertEquals(eth1Data, testEth1Data);
  }

  @Test
  void equalsReturnsFalseWhenDepositRootsAreDifferent() {
    Eth1Data testEth1Data = new Eth1Data(depositRoot.not(), depositCount, blockHash);

    assertNotEquals(eth1Data, testEth1Data);
  }

  @Test
  void equalsReturnsFalseWhenBlockHashesAreDifferent() {
    Eth1Data testEth1Data = new Eth1Data(depositRoot, depositCount, blockHash.not());

    assertNotEquals(eth1Data, testEth1Data);
  }

  @Test
  void roundtripSSZ() {
    Bytes eth1DataSerialized = SimpleOffsetSerializer.serialize(eth1Data);
    Eth1Data newEth1Data = SimpleOffsetSerializer.deserialize(eth1DataSerialized, Eth1Data.class);
    assertEquals(eth1Data, newEth1Data);
  }
}
