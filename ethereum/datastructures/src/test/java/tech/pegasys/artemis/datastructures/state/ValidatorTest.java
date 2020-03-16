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
import org.apache.tuweni.ssz.SSZ;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

class ValidatorTest {

  public static Validator validatorFromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            Validator.create(
                BLSPublicKey.fromBytes(reader.readFixedBytes(48)),
                Bytes32.wrap(reader.readFixedBytes(32)),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                reader.readBoolean(),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64()),
                UnsignedLong.fromLongBits(reader.readUInt64())));
  }

  public static Bytes validatorToBytes(Validator v) {
    return SSZ.encode(
        writer -> {
          writer.writeFixedBytes(v.getPubkey().toBytes());
          writer.writeFixedBytes(v.getWithdrawal_credentials());
          writer.writeUInt64(v.getEffective_balance().longValue());
          writer.writeBoolean(v.isSlashed());
          writer.writeUInt64(v.getActivation_eligibility_epoch().longValue());
          writer.writeUInt64(v.getActivation_epoch().longValue());
          writer.writeUInt64(v.getExit_epoch().longValue());
          writer.writeUInt64(v.getWithdrawable_epoch().longValue());
        });
  }

  private int seed = 100;
  private BLSPublicKey pubkey = BLSPublicKey.random(seed);
  private Bytes32 withdrawalCredentials = randomBytes32(seed++);
  private UnsignedLong activationEligibilityEpoch = randomUnsignedLong(seed++);
  private UnsignedLong activationEpoch = randomUnsignedLong(seed++);
  private UnsignedLong exitEpoch = randomUnsignedLong(seed++);
  private UnsignedLong withdrawalEpoch = randomUnsignedLong(seed++);
  private boolean slashed = false;
  private UnsignedLong effectiveBalance = randomUnsignedLong(seed++);

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
            activationEpoch.plus(randomUnsignedLong(seed++)),
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
            exitEpoch.plus(randomUnsignedLong(seed++)),
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
            withdrawalEpoch.plus(randomUnsignedLong(seed++)));

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
    Bytes sszValidatorBytes = validatorToBytes(validator);
    assertEquals(validator, validatorFromBytes(sszValidatorBytes));
  }
}
