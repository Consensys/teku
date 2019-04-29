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

package tech.pegasys.artemis.datastructures.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomDepositInput;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomLong;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(BouncyCastleExtension.class)
class DepositDataTest {

  private final long amount = randomLong();
  private final long timestamp = randomLong();
  private final DepositInput depositInput = randomDepositInput();

  private final DepositData depositData = new DepositData(amount, timestamp, depositInput);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    DepositData testDepositData = depositData;

    assertEquals(depositData, testDepositData);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    DepositData testDepositData = new DepositData(amount, timestamp, depositInput);

    assertEquals(depositData, testDepositData);
  }

  @Test
  void equalsReturnsFalseWhenAmountsAreDifferent() {
    DepositData testDepositData = new DepositData(amount + randomLong(), timestamp, depositInput);

    assertNotEquals(depositData, testDepositData);
  }

  @Test
  void equalsReturnsFalseWhenTimestampsAreDifferent() {
    DepositData testDepositData = new DepositData(amount, timestamp + randomLong(), depositInput);

    assertNotEquals(depositData, testDepositData);
  }

  @Test
  void equalsReturnsFalseWhenDepositInputsAreDifferent() {
    // DepositInput is rather involved to create. Just create a random one until it is not the same
    // as the original.
    DepositInput otherDepositInput = randomDepositInput();
    while (Objects.equals(otherDepositInput, depositInput)) {
      otherDepositInput = randomDepositInput();
    }

    DepositData testDepositData = new DepositData(amount, timestamp, otherDepositInput);

    assertNotEquals(depositData, testDepositData);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszDepositDataBytes = depositData.toBytes();
    assertEquals(depositData, DepositData.fromBytes(sszDepositDataBytes));
  }
}
