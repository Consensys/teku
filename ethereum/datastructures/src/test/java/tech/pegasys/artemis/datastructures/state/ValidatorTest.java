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

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.bls.BLSPublicKey;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;

class ValidatorTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  private int seed = 100;
  private BLSPublicKey pubkey = BLSPublicKey.random(seed);
  private Bytes32 withdrawalCredentials = dataStructureUtil.randomBytes32();
  private UnsignedLong activationEligibilityEpoch = dataStructureUtil.randomUnsignedLong();
  private UnsignedLong activationEpoch = dataStructureUtil.randomUnsignedLong();
  private UnsignedLong exitEpoch = dataStructureUtil.randomUnsignedLong();
  private UnsignedLong withdrawalEpoch = dataStructureUtil.randomUnsignedLong();
  private boolean slashed = false;
  private UnsignedLong effectiveBalance = dataStructureUtil.randomUnsignedLong();

  private Validator validator =
      Validator.create(
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
    Validator testValidator = validator;

    assertEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    Validator testValidator =
        Validator.create(
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
    BLSPublicKey differentPublicKey = BLSPublicKey.random(99);
    Validator testValidator =
        Validator.create(
            differentPublicKey,
            withdrawalCredentials,
            effectiveBalance,
            slashed,
            activationEligibilityEpoch,
            activationEpoch,
            exitEpoch,
            withdrawalEpoch);

    assertNotEquals(pubkey, differentPublicKey);
    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenWithdrawalCredentialsAreDifferent() {
    Validator testValidator =
        Validator.create(
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
    Validator testValidator =
        Validator.create(
            pubkey,
            withdrawalCredentials,
            effectiveBalance,
            slashed,
            activationEligibilityEpoch,
            activationEpoch.plus(dataStructureUtil.randomUnsignedLong()),
            exitEpoch,
            withdrawalEpoch);

    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenExitEpochsAreDifferent() {
    Validator testValidator =
        Validator.create(
            pubkey,
            withdrawalCredentials,
            effectiveBalance,
            slashed,
            activationEligibilityEpoch,
            activationEpoch,
            exitEpoch.plus(dataStructureUtil.randomUnsignedLong()),
            withdrawalEpoch);

    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenWithdrawalEpochsAreDifferent() {
    Validator testValidator =
        Validator.create(
            pubkey,
            withdrawalCredentials,
            effectiveBalance,
            slashed,
            activationEligibilityEpoch,
            activationEpoch,
            exitEpoch,
            withdrawalEpoch.plus(dataStructureUtil.randomUnsignedLong()));

    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenInitiatedExitIsDifferent() {
    Validator testValidator =
        Validator.create(
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
    Bytes sszValidatorBytes = SimpleOffsetSerializer.serialize(validator);
    assertEquals(validator, SimpleOffsetSerializer.deserialize(sszValidatorBytes, Validator.class));
  }
}
