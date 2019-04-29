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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomLong;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

class ValidatorTest {

  private final BLSPublicKey pubkey = BLSPublicKey.random();
  private final Bytes32 withdrawalCredentials = Bytes32.random();
  private final long activationEpoch = randomLong();
  private final long exitEpoch = randomLong();
  private final long withdrawalEpoch = randomLong();
  private final boolean initiatedExit = false;
  private final boolean slashed = false;

  private final Validator validator =
      new Validator(
          pubkey,
          withdrawalCredentials,
          activationEpoch,
          exitEpoch,
          withdrawalEpoch,
          initiatedExit,
          slashed);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    Validator testValidator = validator;

    assertEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    Validator testValidator =
        new Validator(
            pubkey,
            withdrawalCredentials,
            activationEpoch,
            exitEpoch,
            withdrawalEpoch,
            initiatedExit,
            slashed);

    assertEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenPubkeysAreDifferent() {
    BLSPublicKey differentPublicKey = BLSPublicKey.random();
    while (pubkey.equals(differentPublicKey)) {
      differentPublicKey = BLSPublicKey.random();
    }
    Validator testValidator =
        new Validator(
            differentPublicKey,
            withdrawalCredentials,
            activationEpoch,
            exitEpoch,
            withdrawalEpoch,
            initiatedExit,
            slashed);

    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenWithdrawalCredentialsAreDifferent() {
    Validator testValidator =
        new Validator(
            pubkey,
            withdrawalCredentials.not(),
            activationEpoch,
            exitEpoch,
            withdrawalEpoch,
            initiatedExit,
            slashed);

    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenActivationEpochsAreDifferent() {
    Validator testValidator =
        new Validator(
            pubkey,
            withdrawalCredentials,
            activationEpoch + randomLong(),
            exitEpoch,
            withdrawalEpoch,
            initiatedExit,
            slashed);

    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenExitEpochsAreDifferent() {
    Validator testValidator =
        new Validator(
            pubkey,
            withdrawalCredentials,
            activationEpoch,
            exitEpoch + randomLong(),
            withdrawalEpoch,
            initiatedExit,
            slashed);

    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenWithdrawalEpochsAreDifferent() {
    Validator testValidator =
        new Validator(
            pubkey,
            withdrawalCredentials,
            activationEpoch,
            exitEpoch,
            withdrawalEpoch + randomLong(),
            initiatedExit,
            slashed);

    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenInitiatedExitIsDifferent() {
    Validator testValidator =
        new Validator(
            pubkey,
            withdrawalCredentials,
            activationEpoch,
            exitEpoch,
            withdrawalEpoch,
            !initiatedExit,
            slashed);

    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenSlashedIsDifferent() {
    Validator testValidator =
        new Validator(
            pubkey,
            withdrawalCredentials,
            activationEpoch,
            exitEpoch,
            withdrawalEpoch,
            initiatedExit,
            !slashed);

    assertNotEquals(validator, testValidator);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszValidatorBytes = validator.toBytes();
    assertEquals(validator, Validator.fromBytes(sszValidatorBytes));
  }
}
