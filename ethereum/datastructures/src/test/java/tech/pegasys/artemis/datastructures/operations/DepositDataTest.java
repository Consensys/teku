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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import org.junit.jupiter.api.Test;

class DepositDataTest {

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    DepositInput depositInput = randomDepositInput();
    UnsignedLong value = randomUnsignedLong();
    UnsignedLong timestamp = randomUnsignedLong();

    DepositData dd1 = new DepositData(depositInput, value, timestamp);
    DepositData dd2 = dd1;

    assertEquals(dd1, dd2);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    DepositInput depositInput = randomDepositInput();
    UnsignedLong value = randomUnsignedLong();
    UnsignedLong timestamp = randomUnsignedLong();

    DepositData dd1 = new DepositData(depositInput, value, timestamp);
    DepositData dd2 = new DepositData(depositInput, value, timestamp);

    assertEquals(dd1, dd2);
  }

  @Test
  void equalsReturnsFalseWhenDepositInputsAreDifferent() {
    DepositInput depositInput = randomDepositInput();
    UnsignedLong value = randomUnsignedLong();
    UnsignedLong timestamp = randomUnsignedLong();

    // DepositInput is rather involved to create. Just create a random one until it is not the same
    // as the original.
    DepositInput otherDepositInput = randomDepositInput();
    while (Objects.equals(otherDepositInput, depositInput)) {
      otherDepositInput = randomDepositInput();
    }

    DepositData dd1 = new DepositData(depositInput, value, timestamp);
    DepositData dd2 = new DepositData(otherDepositInput, value, timestamp);

    assertNotEquals(dd1, dd2);
  }

  @Test
  void equalsReturnsFalseWhenValuesAreDifferent() {
    DepositInput depositInput = randomDepositInput();
    UnsignedLong value = randomUnsignedLong();
    UnsignedLong timestamp = randomUnsignedLong();

    DepositData dd1 = new DepositData(depositInput, value, timestamp);
    DepositData dd2 = new DepositData(depositInput, value.plus(randomUnsignedLong()), timestamp);

    assertNotEquals(dd1, dd2);
  }

  @Test
  void equalsReturnsFalseWhenTimestampsAreDifferent() {
    DepositInput depositInput = randomDepositInput();
    UnsignedLong value = randomUnsignedLong();
    UnsignedLong timestamp = randomUnsignedLong();

    DepositData dd1 = new DepositData(depositInput, value, timestamp);
    DepositData dd2 = new DepositData(depositInput, value, timestamp.plus(randomUnsignedLong()));

    assertNotEquals(dd1, dd2);
  }

  @Test
  void rountripSSZ() {
    DepositData depositData =
        new DepositData(randomDepositInput(), randomUnsignedLong(), randomUnsignedLong());
    Bytes sszDepositDataBytes = depositData.toBytes();
    assertEquals(depositData, DepositData.fromBytes(sszDepositDataBytes));
  }
}
