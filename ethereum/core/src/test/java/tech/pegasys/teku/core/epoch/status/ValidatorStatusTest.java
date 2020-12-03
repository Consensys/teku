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

package tech.pegasys.teku.core.epoch.status;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.util.config.Constants.FAR_FUTURE_EPOCH;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class ValidatorStatusTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldSetSlashedCorrectly(final boolean slashed) {
    final Validator validator = dataStructureUtil.randomValidator().withSlashed(slashed);
    assertThat(ValidatorStatus.create(validator, UInt64.ZERO, UInt64.ZERO).isSlashed())
        .isEqualTo(slashed);
  }

  @Test
  void shouldBeWithdrawableWhenWithdrawalEpochBeforeCurrentEpoch() {
    final Validator validator =
        dataStructureUtil.randomValidator().withWithdrawable_epoch(UInt64.valueOf(7));
    assertThat(
            ValidatorStatus.create(validator, UInt64.valueOf(7), UInt64.valueOf(8))
                .isWithdrawableInCurrentEpoch())
        .isTrue();
  }

  @Test
  void shouldBeWithdrawableWhenWithdrawalEpochEqualToCurrentEpoch() {
    final Validator validator =
        dataStructureUtil.randomValidator().withWithdrawable_epoch(UInt64.valueOf(7));
    assertThat(
            ValidatorStatus.create(validator, UInt64.valueOf(6), UInt64.valueOf(7))
                .isWithdrawableInCurrentEpoch())
        .isTrue();
  }

  @Test
  void shouldNotBeWithdrawableWhenWithdrawalEpochAfterCurrentEpoch() {
    final Validator validator =
        dataStructureUtil.randomValidator().withWithdrawable_epoch(UInt64.valueOf(7));
    assertThat(
            ValidatorStatus.create(validator, UInt64.valueOf(5), UInt64.valueOf(6))
                .isWithdrawableInCurrentEpoch())
        .isFalse();
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 1, 3, Integer.MAX_VALUE, Long.MAX_VALUE})
  void shouldSetEffectiveBalanceCorrectly(final long balance) {
    final UInt64 effectiveBalance = UInt64.valueOf(balance);
    final Validator validator =
        dataStructureUtil.randomValidator().withEffective_balance(effectiveBalance);
    assertThat(
            ValidatorStatus.create(validator, UInt64.ZERO, UInt64.ZERO)
                .getCurrentEpochEffectiveBalance())
        .isEqualTo(effectiveBalance);
  }

  @ParameterizedTest
  @MethodSource("activeEpochs")
  void shouldSetActiveInCurrentEpochCorrectly(
      final UInt64 activationEpoch,
      final UInt64 exitEpoch,
      final UInt64 currentEpoch,
      final boolean isActive) {
    final Validator validator =
        dataStructureUtil
            .randomValidator()
            .withActivation_epoch(activationEpoch)
            .withExit_epoch(exitEpoch);
    final ValidatorStatus validatorStatus =
        ValidatorStatus.create(validator, currentEpoch.minusMinZero(1), currentEpoch);
    assertThat(validatorStatus.isActiveInCurrentEpoch()).isEqualTo(isActive);
  }

  @ParameterizedTest
  @MethodSource("activeEpochs")
  void shouldSetActiveInPreviousEpochCorrectly(
      final UInt64 activationEpoch,
      final UInt64 exitEpoch,
      final UInt64 previousEpoch,
      final boolean isActive) {
    final Validator validator =
        dataStructureUtil
            .randomValidator()
            .withActivation_epoch(activationEpoch)
            .withExit_epoch(exitEpoch);
    final ValidatorStatus validatorStatus =
        ValidatorStatus.create(validator, previousEpoch, previousEpoch.plus(1));
    assertThat(validatorStatus.isActiveInPreviousEpoch()).isEqualTo(isActive);
  }

  static Stream<Arguments> activeEpochs() {
    return Stream.of(
        Arguments.of(FAR_FUTURE_EPOCH, FAR_FUTURE_EPOCH, UInt64.valueOf(0), false),
        Arguments.of(UInt64.valueOf(0), FAR_FUTURE_EPOCH, UInt64.valueOf(0), true),
        Arguments.of(UInt64.valueOf(1), UInt64.valueOf(2), UInt64.valueOf(0), false),
        Arguments.of(UInt64.valueOf(1), UInt64.valueOf(2), UInt64.valueOf(1), true),
        Arguments.of(UInt64.valueOf(0), UInt64.valueOf(2), UInt64.valueOf(0), true),
        Arguments.of(UInt64.valueOf(0), UInt64.valueOf(2), UInt64.valueOf(1), true),
        Arguments.of(UInt64.valueOf(0), UInt64.valueOf(2), UInt64.valueOf(2), false),
        Arguments.of(UInt64.valueOf(0), UInt64.valueOf(2), UInt64.valueOf(3), false));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("flags")
  void shouldSetFlagToTrueWheNewValueTrue(
      final String name,
      final Function<ValidatorStatus, Boolean> getter,
      final BiConsumer<ValidatorStatus, Boolean> setter) {
    final ValidatorStatus status =
        ValidatorStatus.create(dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    assertThat(getter.apply(status)).isFalse();
    setter.accept(status, true);
    assertThat(getter.apply(status)).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("flags")
  void shouldLeaveFlagAsFalseWheNewValueFalse(
      final String name,
      final Function<ValidatorStatus, Boolean> getter,
      final BiConsumer<ValidatorStatus, Boolean> setter) {
    final ValidatorStatus status =
        ValidatorStatus.create(dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    assertThat(getter.apply(status)).isFalse();
    setter.accept(status, false);
    assertThat(getter.apply(status)).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("flags")
  void shouldLeaveFlagAsTrueWheNewValueFalse(
      final String name,
      final Function<ValidatorStatus, Boolean> getter,
      final BiConsumer<ValidatorStatus, Boolean> setter) {
    final ValidatorStatus status =
        ValidatorStatus.create(dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    assertThat(getter.apply(status)).isFalse();
    setter.accept(status, true);
    assertThat(getter.apply(status)).isTrue();
    setter.accept(status, false);
    assertThat(getter.apply(status)).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("flags")
  void shouldLeaveFlagAsTrueWheNewValueTrue(
      final String name,
      final Function<ValidatorStatus, Boolean> getter,
      final BiConsumer<ValidatorStatus, Boolean> setter) {
    final ValidatorStatus status =
        ValidatorStatus.create(dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    assertThat(getter.apply(status)).isFalse();
    setter.accept(status, true);
    assertThat(getter.apply(status)).isTrue();
    setter.accept(status, true);
    assertThat(getter.apply(status)).isTrue();
  }

  @Test
  void shouldUpdateInclusionInfoWhenNoPreviousValueSet() {
    final ValidatorStatus status =
        ValidatorStatus.create(dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    final InclusionInfo newValue = new InclusionInfo(UInt64.ONE, UInt64.ONE);
    status.updateInclusionInfo(Optional.of(newValue));
    assertThat(status.getInclusionInfo()).contains(newValue);
  }

  @Test
  void shouldLeaveInclusionInfoEmptyWhenNewValueIsEmpty() {
    final ValidatorStatus status =
        ValidatorStatus.create(dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    status.updateInclusionInfo(Optional.empty());
    assertThat(status.getInclusionInfo()).isEmpty();
  }

  @Test
  void shouldNotChangeInclusionInfoWhenNewValueIsEmpty() {
    final ValidatorStatus status =
        ValidatorStatus.create(dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    final InclusionInfo oldValue = new InclusionInfo(UInt64.ONE, UInt64.ONE);
    status.updateInclusionInfo(Optional.of(oldValue));

    status.updateInclusionInfo(Optional.empty());
    assertThat(status.getInclusionInfo()).contains(oldValue);
  }

  @Test
  void shouldUseNewInclusionInfoWhenItHasLowerDelay() {
    final ValidatorStatus status =
        ValidatorStatus.create(dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    final InclusionInfo oldValue = new InclusionInfo(UInt64.ONE, UInt64.ONE);
    final InclusionInfo newValue = new InclusionInfo(UInt64.ZERO, UInt64.valueOf(3));
    status.updateInclusionInfo(Optional.of(oldValue));

    status.updateInclusionInfo(Optional.of(newValue));
    assertThat(status.getInclusionInfo()).contains(newValue);
  }

  @Test
  void shouldKeepOldInclusionInfoWhenNewValueHasEqualDelay() {
    final ValidatorStatus status =
        ValidatorStatus.create(dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    final InclusionInfo oldValue = new InclusionInfo(UInt64.ONE, UInt64.ONE);
    final InclusionInfo newValue = new InclusionInfo(UInt64.ONE, UInt64.valueOf(3));
    status.updateInclusionInfo(Optional.of(oldValue));

    status.updateInclusionInfo(Optional.of(newValue));
    assertThat(status.getInclusionInfo()).contains(oldValue);
  }

  @Test
  void shouldKeepOldInclusionInfoWhenNewValueHasHigherDelay() {
    final ValidatorStatus status =
        ValidatorStatus.create(dataStructureUtil.randomValidator(), UInt64.ZERO, UInt64.ONE);
    final InclusionInfo oldValue = new InclusionInfo(UInt64.ONE, UInt64.ONE);
    final InclusionInfo newValue = new InclusionInfo(UInt64.valueOf(2), UInt64.valueOf(3));
    status.updateInclusionInfo(Optional.of(oldValue));

    status.updateInclusionInfo(Optional.of(newValue));
    assertThat(status.getInclusionInfo()).contains(oldValue);
  }

  static Stream<Arguments> flags() {
    return Stream.of(
        flag(
            "currentEpochAttester",
            ValidatorStatus::isCurrentEpochAttester,
            ValidatorStatus::updateCurrentEpochAttester),
        flag(
            "currentEpochTargetAttester",
            ValidatorStatus::isCurrentEpochTargetAttester,
            ValidatorStatus::updateCurrentEpochTargetAttester),
        flag(
            "previousEpochAttester",
            ValidatorStatus::isPreviousEpochAttester,
            ValidatorStatus::updatePreviousEpochAttester),
        flag(
            "previousEpochTargetAttester",
            ValidatorStatus::isPreviousEpochTargetAttester,
            ValidatorStatus::updatePreviousEpochTargetAttester),
        flag(
            "previousEpochHeadAttester",
            ValidatorStatus::isPreviousEpochHeadAttester,
            ValidatorStatus::updatePreviousEpochHeadAttester));
  }

  private static Arguments flag(
      final String name,
      final Function<ValidatorStatus, Boolean> getter,
      final BiConsumer<ValidatorStatus, Boolean> setter) {
    return Arguments.of(name, getter, setter);
  }
}
