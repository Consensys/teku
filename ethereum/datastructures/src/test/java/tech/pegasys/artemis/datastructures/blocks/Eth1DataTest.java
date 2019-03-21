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

import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class Eth1DataTest {

  private Bytes32 depositRoot = Bytes32.random();
  private Bytes32 blockHash = Bytes32.random();

  private Eth1Data eth1Data = new Eth1Data(depositRoot, blockHash);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    Eth1Data testEth1Data = eth1Data;

    assertEquals(eth1Data, testEth1Data);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    Eth1Data testEth1Data = new Eth1Data(depositRoot, blockHash);

    assertEquals(eth1Data, testEth1Data);
  }

  @Test
  void equalsReturnsFalseWhenDepositRootsAreDifferent() {
    Eth1Data testEth1Data = new Eth1Data(depositRoot.not(), blockHash);

    assertNotEquals(eth1Data, testEth1Data);
  }

  @Test
  void equalsReturnsFalseWhenBlockHashesAreDifferent() {
    Eth1Data testEth1Data = new Eth1Data(depositRoot, blockHash.not());

    assertNotEquals(eth1Data, testEth1Data);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszEth1DataBytes = eth1Data.toBytes();
    assertEquals(eth1Data, Eth1Data.fromBytes(sszEth1DataBytes));
  }
}
