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

package tech.pegasys.artemis.datastructures.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomBytes32;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

class ValidatorTest {
  private int seed = 100;
  private BLSPublicKey pubkey = BLSPublicKey.random(seed);
  private Bytes32 withdrawalCredentials = randomBytes32(seed++);
  private UnsignedLong activationEligibilityEpoch = randomUnsignedLong(seed++);
  private UnsignedLong activationEpoch = randomUnsignedLong(seed++);
  private UnsignedLong exitEpoch = randomUnsignedLong(seed++);
  private UnsignedLong withdrawalEpoch = randomUnsignedLong(seed++);
  private boolean slashed = false;
  private UnsignedLong effectiveBalance = randomUnsignedLong(seed++);

  private ValidatorImpl validator =
      new ValidatorImpl(
          pubkey,
          withdrawalCredentials,
          effectiveBalance,
          slashed,
          activationEligibilityEpoch,
          activationEpoch,
          exitEpoch,
          withdrawalEpoch);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    ValidatorImpl testValidator = validator;

    assertEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    ValidatorImpl testValidator =
        new ValidatorImpl(
            pubkey,
            withdrawalCredentials,
            effectiveBalance,
            slashed,
            activationEligibilityEpoch,
            activationEpoch,
            exitEpoch,
            withdrawalEpoch);

    assertEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenPubkeysAreDifferent() {
    BLSPublicKey differentPublicKey = BLSPublicKey.random();
    while (pubkey.equals(differentPublicKey)) {
      differentPublicKey = BLSPublicKey.random();
    }
    ValidatorImpl testValidator =
        new ValidatorImpl(
            differentPublicKey,
            withdrawalCredentials,
            effectiveBalance,
            slashed,
            activationEligibilityEpoch,
            activationEpoch,
            exitEpoch,
            withdrawalEpoch);

    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenWithdrawalCredentialsAreDifferent() {
    ValidatorImpl testValidator =
        new ValidatorImpl(
            pubkey,
            withdrawalCredentials.not(),
            effectiveBalance,
            slashed,
            activationEligibilityEpoch,
            activationEpoch,
            exitEpoch,
            withdrawalEpoch);

    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenActivationEpochsAreDifferent() {
    ValidatorImpl testValidator =
        new ValidatorImpl(
            pubkey,
            withdrawalCredentials,
            effectiveBalance,
            slashed,
            activationEligibilityEpoch,
            activationEpoch.plus(randomUnsignedLong(seed++)),
            exitEpoch,
            withdrawalEpoch);

    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenExitEpochsAreDifferent() {
    ValidatorImpl testValidator =
        new ValidatorImpl(
            pubkey,
            withdrawalCredentials,
            effectiveBalance,
            slashed,
            activationEligibilityEpoch,
            activationEpoch,
            exitEpoch.plus(randomUnsignedLong(seed++)),
            withdrawalEpoch);

    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenWithdrawalEpochsAreDifferent() {
    ValidatorImpl testValidator =
        new ValidatorImpl(
            pubkey,
            withdrawalCredentials,
            effectiveBalance,
            slashed,
            activationEligibilityEpoch,
            activationEpoch,
            exitEpoch,
            withdrawalEpoch.plus(randomUnsignedLong(seed++)));

    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenInitiatedExitIsDifferent() {
    ValidatorImpl testValidator =
        new ValidatorImpl(
            pubkey,
            withdrawalCredentials,
            effectiveBalance,
            !slashed,
            activationEligibilityEpoch,
            activationEpoch,
            exitEpoch,
            withdrawalEpoch);

    assertNotEquals(validator, testValidator);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszValidatorBytes = validator.toBytes();
    assertEquals(validator, ValidatorImpl.fromBytes(sszValidatorBytes));
  }
}
