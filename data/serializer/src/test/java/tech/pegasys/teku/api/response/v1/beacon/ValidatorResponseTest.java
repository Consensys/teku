/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.api.response.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.util.config.Constants.FAR_FUTURE_EPOCH;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ValidatorResponseTest {
  final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  final Bytes48 key = dataStructureUtil.randomPublicKeyBytes();
  final Bytes32 creds = dataStructureUtil.randomBytes32();
  final UInt64 ONE_HUNDRED = UInt64.valueOf(100);
  final UInt64 TWO_HUNDRED = UInt64.valueOf(200);

  @Test
  void status_shouldBePendingInitialised() {
    final Validator validator = pendingValidator(TWO_HUNDRED, FAR_FUTURE_EPOCH);
    assertThat(ValidatorResponse.getValidatorStatus(ONE_HUNDRED, validator))
        .isEqualTo(ValidatorStatus.pending_initialized);
  }

  @Test
  void status_shouldBePendingQueued() {
    final Validator validator = pendingValidator(TWO_HUNDRED, TWO_HUNDRED);
    assertThat(ValidatorResponse.getValidatorStatus(ONE_HUNDRED, validator))
        .isEqualTo(ValidatorStatus.pending_queued);
  }

  @Test
  void status_shouldBeActiveOngoing() {
    final Validator validator = activeValidator(FAR_FUTURE_EPOCH, false);
    assertThat(ValidatorResponse.getValidatorStatus(ZERO, validator))
        .isEqualTo(ValidatorStatus.active_ongoing);
  }

  @Test
  void status_shouldBeActiveExiting() {
    final Validator validator = activeValidator(TWO_HUNDRED, false);
    assertThat(ValidatorResponse.getValidatorStatus(ONE_HUNDRED, validator))
        .isEqualTo(ValidatorStatus.active_exiting);
  }

  @Test
  void status_shouldBeActiveSlashed() {
    final Validator validator = activeValidator(TWO_HUNDRED, true);
    assertThat(ValidatorResponse.getValidatorStatus(ONE_HUNDRED, validator))
        .isEqualTo(ValidatorStatus.active_slashed);
  }

  @Test
  void status_shouldBeExitedUnslashed() {
    final Validator validator = exitedValidator(ONE_HUNDRED, TWO_HUNDRED, false);
    assertThat(ValidatorResponse.getValidatorStatus(ONE_HUNDRED, validator))
        .isEqualTo(ValidatorStatus.exited_unslashed);
    assertThat(ValidatorResponse.getValidatorStatus(ONE_HUNDRED.plus(ONE), validator))
        .isEqualTo(ValidatorStatus.exited_unslashed);
  }

  @Test
  void status_shouldBeExitedSlashed() {
    final Validator validator = exitedValidator(ONE_HUNDRED, TWO_HUNDRED, true);
    assertThat(ValidatorResponse.getValidatorStatus(ONE_HUNDRED, validator))
        .isEqualTo(ValidatorStatus.exited_slashed);
  }

  @Test
  void status_shouldBeWithdrawalPossible() {
    final Validator validator = withdrawalValidator(UInt64.valueOf("32000000000"), ONE_HUNDRED);
    assertThat(ValidatorResponse.getValidatorStatus(ONE_HUNDRED, validator))
        .isEqualTo(ValidatorStatus.withdrawal_possible);
  }

  @Test
  void status_shouldBeWithdrawalDone() {
    final Validator validator = withdrawalValidator(ZERO, ONE_HUNDRED);
    assertThat(ValidatorResponse.getValidatorStatus(ONE_HUNDRED, validator))
        .isEqualTo(ValidatorStatus.withdrawal_done);
  }

  private Validator withdrawalValidator(final UInt64 balance, final UInt64 withdrawable_epoch) {
    return createValidator(balance, false, ZERO, ZERO, ZERO, withdrawable_epoch);
  }

  private Validator exitedValidator(
      final UInt64 exit_epoch, final UInt64 withdrawable_epoch, final boolean slashed) {
    return createValidator(
        UInt64.valueOf("32000000000"), slashed, ZERO, ZERO, exit_epoch, withdrawable_epoch);
  }

  private Validator activeValidator(final UInt64 exit_epoch, final boolean slashed) {
    return createValidator(
        UInt64.valueOf("32000000000"), slashed, ZERO, ZERO, exit_epoch, FAR_FUTURE_EPOCH);
  }

  private Validator pendingValidator(
      final UInt64 activation_epoch, final UInt64 activation_eligibility_epoch) {
    return createValidator(
        UInt64.valueOf("32000000000"),
        false,
        activation_eligibility_epoch,
        activation_epoch,
        FAR_FUTURE_EPOCH,
        FAR_FUTURE_EPOCH);
  }

  private Validator createValidator(
      final UInt64 balance,
      final boolean slashed,
      final UInt64 activation_eligibility_epoch,
      final UInt64 activation_epoch,
      final UInt64 exit_epoch,
      final UInt64 withdrawable_epoch) {
    return new Validator(
        key,
        creds,
        balance,
        slashed,
        activation_eligibility_epoch,
        activation_epoch,
        exit_epoch,
        withdrawable_epoch);
  }
}
