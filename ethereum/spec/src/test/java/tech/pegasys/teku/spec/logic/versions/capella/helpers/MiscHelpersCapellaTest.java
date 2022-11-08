/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.versions.capella.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class MiscHelpersCapellaTest {

  private SpecConfigCapella specConfigCapella;
  private DataStructureUtil dataStructureUtil;
  private MiscHelpersCapella miscHelpersCapella;
  private Bytes32 eth1WithdrawalsCredentials;
  private Bytes32 blsWithdrawalsCredentials;

  @BeforeEach
  public void before() {
    final Spec spec = TestSpecFactory.createMainnet(SpecMilestone.CAPELLA);
    this.specConfigCapella = spec.getGenesisSpecConfig().toVersionCapella().orElseThrow();
    this.dataStructureUtil = new DataStructureUtil(spec);
    this.miscHelpersCapella = new MiscHelpersCapella(specConfigCapella);
    this.eth1WithdrawalsCredentials = dataStructureUtil.randomEth1WithdrawalCredentials();
    this.blsWithdrawalsCredentials = dataStructureUtil.randomBlsWithdrawalCredentials();
  }

  @Test
  public void isFullyWithdrawableValidatorShouldReturnTrueForEligibleValidator() {
    final Validator validator =
        dataStructureUtil
            .randomValidator()
            .withWithdrawalCredentials(eth1WithdrawalsCredentials)
            .withWithdrawableEpoch(UInt64.ZERO);

    final UInt64 balance = UInt64.ONE;
    final UInt64 epoch = UInt64.ONE;

    assertThat(miscHelpersCapella.isFullyWithdrawableValidator(validator, balance, epoch)).isTrue();
  }

  @Test
  public void isFullyWithdrawableValidatorShouldReturnFalseForNonEth1Credentials() {
    final Validator validator =
        dataStructureUtil
            .randomValidator()
            .withWithdrawalCredentials(blsWithdrawalsCredentials)
            .withWithdrawableEpoch(UInt64.ZERO);

    final UInt64 balance = UInt64.ONE;
    final UInt64 epoch = UInt64.ONE;

    assertThat(miscHelpersCapella.isFullyWithdrawableValidator(validator, balance, epoch))
        .isFalse();
  }

  @Test
  public void isFullyWithdrawableValidatorShouldReturnFalseForZeroBalance() {
    final Validator validator =
        dataStructureUtil
            .randomValidator()
            .withWithdrawalCredentials(eth1WithdrawalsCredentials)
            .withWithdrawableEpoch(UInt64.ZERO);

    final UInt64 balance = UInt64.ZERO;
    final UInt64 epoch = UInt64.ONE;

    assertThat(miscHelpersCapella.isFullyWithdrawableValidator(validator, balance, epoch))
        .isFalse();
  }

  @Test
  public void isFullyWithdrawableValidatorShouldReturnFalseForWithdrawableEpochInTheFuture() {
    final Validator validator =
        dataStructureUtil
            .randomValidator()
            .withWithdrawalCredentials(eth1WithdrawalsCredentials)
            .withWithdrawableEpoch(UInt64.MAX_VALUE);

    final UInt64 balance = UInt64.ZERO;
    final UInt64 epoch = UInt64.ONE;

    assertThat(miscHelpersCapella.isFullyWithdrawableValidator(validator, balance, epoch))
        .isFalse();
  }

  @Test
  public void isPartiallyWithdrawableValidatorShouldReturnTrueForEligibleValidator() {
    final Validator validator =
        dataStructureUtil
            .randomValidator()
            .withWithdrawalCredentials(eth1WithdrawalsCredentials)
            .withEffectiveBalance(specConfigCapella.getMaxEffectiveBalance());

    final UInt64 balance = specConfigCapella.getMaxEffectiveBalance().plus(UInt64.ONE);

    assertThat(miscHelpersCapella.isPartiallyWithdrawableValidator(validator, balance)).isTrue();
  }

  @Test
  public void isPartiallyWithdrawableValidatorShouldReturnFalseForNonEth1Credentials() {
    final Validator validator =
        dataStructureUtil
            .randomValidator()
            .withWithdrawalCredentials(blsWithdrawalsCredentials)
            .withEffectiveBalance(specConfigCapella.getMaxEffectiveBalance());

    final UInt64 balance = specConfigCapella.getMaxEffectiveBalance().plus(UInt64.ONE);

    assertThat(miscHelpersCapella.isPartiallyWithdrawableValidator(validator, balance)).isFalse();
  }

  @Test
  public void
      isPartiallyWithdrawableValidatorShouldReturnFalseForEffectiveBalanceLessThanMaxEffectiveBalance() {
    final Validator validator =
        dataStructureUtil
            .randomValidator()
            .withWithdrawalCredentials(eth1WithdrawalsCredentials)
            .withEffectiveBalance(specConfigCapella.getMaxEffectiveBalance().minus(UInt64.ONE));

    final UInt64 balance = specConfigCapella.getMaxEffectiveBalance().plus(UInt64.ONE);

    assertThat(miscHelpersCapella.isPartiallyWithdrawableValidator(validator, balance)).isFalse();
  }

  @Test
  public void
      isPartiallyWithdrawableValidatorShouldReturnFalseForBalanceLowerThanMaxEffectiveBalance() {
    final Validator validator =
        dataStructureUtil
            .randomValidator()
            .withWithdrawalCredentials(eth1WithdrawalsCredentials)
            .withEffectiveBalance(specConfigCapella.getMaxEffectiveBalance());

    final UInt64 balance = specConfigCapella.getMaxEffectiveBalance().minus(UInt64.ONE);

    assertThat(miscHelpersCapella.isPartiallyWithdrawableValidator(validator, balance)).isFalse();
  }

  @Test
  public void
      isPartiallyWithdrawableValidatorShouldReturnFalseForBalanceEqualToMaxEffectiveBalance() {
    final Validator validator =
        dataStructureUtil
            .randomValidator()
            .withWithdrawalCredentials(eth1WithdrawalsCredentials)
            .withEffectiveBalance(specConfigCapella.getMaxEffectiveBalance());

    final UInt64 balance = specConfigCapella.getMaxEffectiveBalance();

    assertThat(miscHelpersCapella.isPartiallyWithdrawableValidator(validator, balance)).isFalse();
  }
}
