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

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.util.config.Constants.EFFECTIVE_BALANCE_INCREMENT;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.independent.TotalBalances;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class TotalBalancesTest {

  private UInt64 balance(final int amount) {
    return EFFECTIVE_BALANCE_INCREMENT.times(amount);
  }

  @Test
  void shouldAddCurrentEpochActiveBalance() {
    final List<ValidatorStatus> statuses =
        List.of(
            new ValidatorStatus(false, false, balance(7), true, false),
            new ValidatorStatus(true, true, balance(5), true, true),
            new ValidatorStatus(false, false, balance(13), false, false));
    // Should include both statuses active in current epoch for a total of 12.
    assertThat(ValidatorStatuses.createTotalBalances(statuses).getCurrentEpoch())
        .isEqualTo(balance(12));
  }

  @Test
  void shouldAddPreviousEpochActiveBalance() {
    final List<ValidatorStatus> statuses =
        List.of(
            new ValidatorStatus(false, false, balance(7), false, true),
            new ValidatorStatus(true, true, balance(5), true, true),
            new ValidatorStatus(false, false, balance(13), false, false));
    // Should include both statuses active in previous epoch for a total of 12.
    assertThat(ValidatorStatuses.createTotalBalances(statuses).getPreviousEpoch())
        .isEqualTo(balance(12));
  }

  @Test
  void shouldExcludeSlashedValidatorsFromAttestersTotals() {
    final List<ValidatorStatus> statuses =
        List.of(
            createWithAllAttesterFlags(true, 8),
            createWithAllAttesterFlags(false, 11),
            createWithAllAttesterFlags(false, 6));

    final UInt64 expectedBalance = balance(11 + 6);
    final TotalBalances balances = ValidatorStatuses.createTotalBalances(statuses);
    assertThat(balances.getCurrentEpochAttesters()).isEqualTo(expectedBalance);
    assertThat(balances.getCurrentEpochTargetAttesters()).isEqualTo(expectedBalance);
    assertThat(balances.getPreviousEpochAttesters()).isEqualTo(expectedBalance);
    assertThat(balances.getPreviousEpochTargetAttesters()).isEqualTo(expectedBalance);
    assertThat(balances.getPreviousEpochHeadAttesters()).isEqualTo(expectedBalance);
  }

  @Test
  void shouldCalculateCurrentEpochAttestersBalances() {
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

    final TotalBalances balances = ValidatorStatuses.createTotalBalances(statuses);
    assertThat(balances.getCurrentEpochAttesters()).isEqualTo(balance(7 + 9 + 14));
    assertThat(balances.getCurrentEpochTargetAttesters()).isEqualTo(balance(7 + 9));
  }

  @Test
  void shouldCalculatePreviousEpochAttestersBalances() {
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

    final TotalBalances balances = ValidatorStatuses.createTotalBalances(statuses);
    assertThat(balances.getPreviousEpochAttesters()).isEqualTo(balance(7 + 9 + 14));
    assertThat(balances.getPreviousEpochTargetAttesters()).isEqualTo(balance(7 + 9));
    assertThat(balances.getPreviousEpochHeadAttesters()).isEqualTo(balance(9));
  }

  @Test
  void shouldReturnMinimumOfOneEffectiveBalanceIncrement() {
    final TotalBalances balances = ValidatorStatuses.createTotalBalances(emptyList());
    assertThat(balances.getCurrentEpoch()).isEqualTo(EFFECTIVE_BALANCE_INCREMENT);
    assertThat(balances.getPreviousEpoch()).isEqualTo(EFFECTIVE_BALANCE_INCREMENT);
    assertThat(balances.getCurrentEpochAttesters()).isEqualTo(EFFECTIVE_BALANCE_INCREMENT);
    assertThat(balances.getCurrentEpochTargetAttesters()).isEqualTo(EFFECTIVE_BALANCE_INCREMENT);
    assertThat(balances.getPreviousEpochAttesters()).isEqualTo(EFFECTIVE_BALANCE_INCREMENT);
    assertThat(balances.getPreviousEpochTargetAttesters()).isEqualTo(EFFECTIVE_BALANCE_INCREMENT);
    assertThat(balances.getPreviousEpochHeadAttesters()).isEqualTo(EFFECTIVE_BALANCE_INCREMENT);
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
