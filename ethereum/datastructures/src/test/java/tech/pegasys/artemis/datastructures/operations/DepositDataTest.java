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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomBytes32;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

class DepositDataTest {
  private int seed = 100;
  private BLSPublicKey pubkey = BLSPublicKey.random(seed++);
  private Bytes32 withdrawalCredentials = randomBytes32(seed++);
  private UnsignedLong amount = randomUnsignedLong(seed++);
  private BLSSignature signature = BLSSignature.random(seed++);

  private DepositData depositData =
      new DepositData(pubkey, withdrawalCredentials, amount, signature);

  @Test
  void equalsReturnsTrueWhenObjectsAreSame() {
    DepositData testDepositInput = depositData;

    assertEquals(depositData, testDepositInput);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    DepositData testDepositInput =
        new DepositData(pubkey, withdrawalCredentials, amount, signature);

    assertEquals(depositData, testDepositInput);
  }

  @Test
  void equalsReturnsFalseWhenPubkeysAreDifferent() {
    BLSPublicKey differentPublicKey = BLSPublicKey.random(99);
    DepositData testDepositInput =
        new DepositData(differentPublicKey, withdrawalCredentials, amount, signature);

    assertNotEquals(pubkey, differentPublicKey);
    assertNotEquals(depositData, testDepositInput);
  }

  @Test
  void equalsReturnsFalseWhenWithdrawalCredentialsAreDifferent() {
    DepositData testDepositInput =
        new DepositData(pubkey, withdrawalCredentials.not(), amount, signature);

    assertNotEquals(depositData, testDepositInput);
  }

  @Test
  void equalsReturnsFalseWhenProofsOfPosessionAreDifferent() {
    BLSSignature differentSignature = BLSSignature.random(99);
    DepositData testDepositInput =
        new DepositData(pubkey, withdrawalCredentials, amount, differentSignature);

    assertNotEquals(signature, differentSignature);
    assertNotEquals(depositData, testDepositInput);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszDepositInputBytes = depositData.toBytes();
    assertEquals(depositData, DepositData.fromBytes(sszDepositInputBytes));
  }
}
