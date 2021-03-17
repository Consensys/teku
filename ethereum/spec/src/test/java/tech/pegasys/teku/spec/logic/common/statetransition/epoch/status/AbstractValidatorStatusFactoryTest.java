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

package tech.pegasys.teku.spec.logic.common.statetransition.epoch.status;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.independent.TotalBalances;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public abstract class AbstractValidatorStatusFactoryTest {

  protected final Spec spec = SpecFactory.createMinimal();
  private final AbstractValidatorStatusFactory validatorStatusFactory = createFactory();
  private final SpecConfig genesisConstants = spec.getGenesisSpecConstants();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private UInt64 balance(final int amount) {
    return genesisConstants.getEffectiveBalanceIncrement().times(amount);
  }

  protected abstract AbstractValidatorStatusFactory createFactory();

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void createValidatorStatus_shouldSetSlashedCorrectly(final boolean slashed) {
    final Validator validator = dataStructureUtil.randomValidator().withSlashed(slashed);
    assertThat(
            validatorStatusFactory
                .createValidatorStatus(validator, UInt64.ZERO, UInt64.ZERO)
                .isSlashed())
        .isEqualTo(slashed);
  }

  @Test
  void createValidatorStatus_shouldBeWithdrawableWhenWithdrawalEpochBeforeCurrentEpoch() {
    final Validator validator =
        dataStructureUtil.randomValidator().withWithdrawable_epoch(UInt64.valueOf(7));
    assertThat(
            validatorStatusFactory
                .createValidatorStatus(validator, UInt64.valueOf(7), UInt64.valueOf(8))
                .isWithdrawableInCurrentEpoch())
        .isTrue();
  }

  @Test
  void createValidatorStatus_shouldBeWithdrawableWhenWithdrawalEpochEqualToCurrentEpoch() {
    final Validator validator =
        dataStructureUtil.randomValidator().withWithdrawable_epoch(UInt64.valueOf(7));
    assertThat(
            validatorStatusFactory
                .createValidatorStatus(validator, UInt64.valueOf(6), UInt64.valueOf(7))
                .isWithdrawableInCurrentEpoch())
        .isTrue();
  }

  @Test
  void createValidatorStatus_shouldNotBeWithdrawableWhenWithdrawalEpochAfterCurrentEpoch() {
    final Validator validator =
        dataStructureUtil.randomValidator().withWithdrawable_epoch(UInt64.valueOf(7));
    assertThat(
            validatorStatusFactory
                .createValidatorStatus(validator, UInt64.valueOf(5), UInt64.valueOf(6))
                .isWithdrawableInCurrentEpoch())
        .isFalse();
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 1, 3, Integer.MAX_VALUE, Long.MAX_VALUE})
  void createValidatorStatus_shouldSetEffectiveBalanceCorrectly(final long balance) {
    final UInt64 effectiveBalance = UInt64.valueOf(balance);
    final Validator validator =
        dataStructureUtil.randomValidator().withEffective_balance(effectiveBalance);
    assertThat(
            validatorStatusFactory
                .createValidatorStatus(validator, UInt64.ZERO, UInt64.ZERO)
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
        validatorStatusFactory.createValidatorStatus(
            validator, currentEpoch.minusMinZero(1), currentEpoch);
    assertThat(validatorStatus.isActiveInCurrentEpoch()).isEqualTo(isActive);
  }

  @ParameterizedTest
  @MethodSource("activeEpochs")
  void createValidatorStatus_shouldSetActiveInPreviousEpochCorrectly(
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
        validatorStatusFactory.createValidatorStatus(
            validator, previousEpoch, previousEpoch.plus(1));
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

  @Test
  void createTotalBalances_shouldAddCurrentEpochActiveBalance() {
    final List<ValidatorStatus> statuses =
        List.of(
            new ValidatorStatus(false, false, balance(7), true, false),
            new ValidatorStatus(true, true, balance(5), true, true),
            new ValidatorStatus(false, false, balance(13), false, false));
    // Should include both statuses active in current epoch for a total of 12.
    assertThat(validatorStatusFactory.createTotalBalances(statuses).getCurrentEpoch())
        .isEqualTo(balance(12));
  }

  @Test
  void createTotalBalances_shouldAddPreviousEpochActiveBalance() {
    final List<ValidatorStatus> statuses =
        List.of(
            new ValidatorStatus(false, false, balance(7), false, true),
            new ValidatorStatus(true, true, balance(5), true, true),
            new ValidatorStatus(false, false, balance(13), false, false));
    // Should include both statuses active in previous epoch for a total of 12.
    assertThat(validatorStatusFactory.createTotalBalances(statuses).getPreviousEpoch())
        .isEqualTo(balance(12));
  }

  @Test
  void createTotalBalances_shouldExcludeSlashedValidatorsFromAttestersTotals() {
    final List<ValidatorStatus> statuses =
        List.of(
            createWithAllAttesterFlags(true, 8),
            createWithAllAttesterFlags(false, 11),
            createWithAllAttesterFlags(false, 6));

    final UInt64 expectedBalance = balance(11 + 6);
    final TotalBalances balances = validatorStatusFactory.createTotalBalances(statuses);
    assertThat(balances.getCurrentEpochAttesters()).isEqualTo(expectedBalance);
    assertThat(balances.getCurrentEpochTargetAttesters()).isEqualTo(expectedBalance);
    assertThat(balances.getPreviousEpochAttesters()).isEqualTo(expectedBalance);
    assertThat(balances.getPreviousEpochTargetAttesters()).isEqualTo(expectedBalance);
    assertThat(balances.getPreviousEpochHeadAttesters()).isEqualTo(expectedBalance);
  }

  @Test
  void createTotalBalances_shouldCalculateCurrentEpochAttestersBalances() {
    final List<ValidatorStatus> statuses =
        List.of(
            createValidator(7)
                .updateCurrentEpochAttester(true)
                .updateCurrentEpochTargetAttester(true),
            createValidator(9)
                .updateCurrentEpochAttester(true)
                .updateCurrentEpochTargetAttester(true),
            createValidator(14).updateCurrentEpochAttester(true),
            createValidator(17)
                .updateCurrentEpochAttester(false)
                .updatePreviousEpochAttester(true));

    final TotalBalances balances = validatorStatusFactory.createTotalBalances(statuses);
    assertThat(balances.getCurrentEpochAttesters()).isEqualTo(balance(7 + 9 + 14));
    assertThat(balances.getCurrentEpochTargetAttesters()).isEqualTo(balance(7 + 9));
  }

  @Test
  void createTotalBalances_shouldCalculatePreviousEpochAttestersBalances() {
    final List<ValidatorStatus> statuses =
        List.of(
            createValidator(7)
                .updatePreviousEpochAttester(true)
                .updatePreviousEpochTargetAttester(true),
            createValidator(9)
                .updatePreviousEpochAttester(true)
                .updatePreviousEpochTargetAttester(true)
                .updatePreviousEpochHeadAttester(true),
            createValidator(14).updatePreviousEpochAttester(true),
            createValidator(17).updateCurrentEpochAttester(true));

    final TotalBalances balances = validatorStatusFactory.createTotalBalances(statuses);
    assertThat(balances.getPreviousEpochAttesters()).isEqualTo(balance(7 + 9 + 14));
    assertThat(balances.getPreviousEpochTargetAttesters()).isEqualTo(balance(7 + 9));
    assertThat(balances.getPreviousEpochHeadAttesters()).isEqualTo(balance(9));
  }

  @Test
  void createTotalBalances_shouldReturnMinimumOfOneEffectiveBalanceIncrement() {
    final TotalBalances balances = validatorStatusFactory.createTotalBalances(emptyList());
    final UInt64 effectiveBalanceInc = genesisConstants.getEffectiveBalanceIncrement();

    assertThat(balances.getCurrentEpoch()).isEqualTo(effectiveBalanceInc);
    assertThat(balances.getPreviousEpoch()).isEqualTo(effectiveBalanceInc);
    assertThat(balances.getCurrentEpochAttesters()).isEqualTo(effectiveBalanceInc);
    assertThat(balances.getCurrentEpochTargetAttesters()).isEqualTo(effectiveBalanceInc);
    assertThat(balances.getPreviousEpochAttesters()).isEqualTo(effectiveBalanceInc);
    assertThat(balances.getPreviousEpochTargetAttesters()).isEqualTo(effectiveBalanceInc);
    assertThat(balances.getPreviousEpochHeadAttesters()).isEqualTo(effectiveBalanceInc);
  }

  private ValidatorStatus createValidator(final int effectiveBalance) {
    return new ValidatorStatus(false, false, balance(effectiveBalance), true, true);
  }

  private ValidatorStatus createWithAllAttesterFlags(
      final boolean slashed, final int effectiveBalance) {
    return new ValidatorStatus(slashed, true, balance(effectiveBalance), true, true)
        .updateCurrentEpochAttester(true)
        .updatePreviousEpochAttester(true)
        .updateCurrentEpochTargetAttester(true)
        .updatePreviousEpochTargetAttester(true)
        .updatePreviousEpochHeadAttester(true);
  }
}
