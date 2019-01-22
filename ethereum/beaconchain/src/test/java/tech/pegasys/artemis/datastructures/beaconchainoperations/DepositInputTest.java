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

package tech.pegasys.artemis.datastructures.beaconchainoperations;

import static org.junit.Assert.assertEquals;

import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import org.junit.Test;

public class DepositInputTest {

  @Test
  public void rountripSSZ() {
    DepositInput di =
        new DepositInput(
            Bytes32.random(),
            new Bytes48[] {Bytes48.random(), Bytes48.random(), Bytes48.random()},
            Bytes48.random(),
            Bytes32.random(),
            Bytes32.random());
    Bytes sszBytes = di.toBytes();
    assertEquals(di, DepositInput.fromBytes(sszBytes));
  }
}
