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

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.datastructures.util.ValidatorsUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ValidatorStatus {
  private final UInt64 currentEpochEffectiveBalance;
  private final boolean slashed;
  private final boolean withdrawableInCurrentEpoch;
  private final boolean activeInCurrentEpoch;
  private final boolean activeInPreviousEpoch;

  private boolean currentEpochAttester = false;
  private boolean currentEpochTargetAttester = false;
  private boolean previousEpochAttester = false;
  private boolean previousEpochTargetAttester = false;
  private boolean previousEpochHeadAttester = false;

  private Optional<InclusionInfo> inclusionInfo = Optional.empty();

  @VisibleForTesting
  ValidatorStatus(
      final boolean slashed,
      final boolean withdrawableInCurrentEpoch,
      final UInt64 currentEpochEffectiveBalance,
      final boolean activeInCurrentEpoch,
      final boolean activeInPreviousEpoch) {
    this.slashed = slashed;
    this.withdrawableInCurrentEpoch = withdrawableInCurrentEpoch;
    this.currentEpochEffectiveBalance = currentEpochEffectiveBalance;
    this.activeInCurrentEpoch = activeInCurrentEpoch;
    this.activeInPreviousEpoch = activeInPreviousEpoch;
  }

  public static ValidatorStatus create(
      final Validator validator, final UInt64 previousEpoch, final UInt64 currentEpoch) {

    return new ValidatorStatus(
        validator.isSlashed(),
        validator.getWithdrawable_epoch().isLessThanOrEqualTo(currentEpoch),
        validator.getEffective_balance(),
        ValidatorsUtil.is_active_validator(validator, currentEpoch),
        ValidatorsUtil.is_active_validator(validator, previousEpoch));
  }

  public boolean isEligibleValidator() {
    return activeInPreviousEpoch || (slashed && !withdrawableInCurrentEpoch);
  }

  public boolean isSlashed() {
    return slashed;
  }

  public boolean isWithdrawableInCurrentEpoch() {
    return withdrawableInCurrentEpoch;
  }

  public boolean isActiveInCurrentEpoch() {
    return activeInCurrentEpoch;
  }

  public boolean isActiveInPreviousEpoch() {
    return activeInPreviousEpoch;
  }

  public UInt64 getCurrentEpochEffectiveBalance() {
    return currentEpochEffectiveBalance;
  }

  public boolean isCurrentEpochAttester() {
    return currentEpochAttester;
  }

  public boolean isCurrentEpochTargetAttester() {
    return currentEpochTargetAttester;
  }

  public boolean isPreviousEpochAttester() {
    return previousEpochAttester;
  }

  public boolean isPreviousEpochTargetAttester() {
    return previousEpochTargetAttester;
  }

  public boolean isPreviousEpochHeadAttester() {
    return previousEpochHeadAttester;
  }

  public Optional<InclusionInfo> getInclusionInfo() {
    return inclusionInfo;
  }

  ValidatorStatus updateCurrentEpochAttester(final boolean currentEpochAttester) {
    this.currentEpochAttester |= currentEpochAttester;
    return this;
  }

  ValidatorStatus updateCurrentEpochTargetAttester(final boolean currentEpochTargetAttester) {
    this.currentEpochTargetAttester |= currentEpochTargetAttester;
    return this;
  }

  ValidatorStatus updatePreviousEpochAttester(final boolean previousEpochAttester) {
    this.previousEpochAttester |= previousEpochAttester;
    return this;
  }

  ValidatorStatus updatePreviousEpochTargetAttester(final boolean previousEpochTargetAttester) {
    this.previousEpochTargetAttester |= previousEpochTargetAttester;
    return this;
  }

  ValidatorStatus updatePreviousEpochHeadAttester(final boolean previousEpochHeadAttester) {
    this.previousEpochHeadAttester |= previousEpochHeadAttester;
    return this;
  }

  ValidatorStatus updateInclusionInfo(final Optional<InclusionInfo> inclusionInfo) {
    if (inclusionInfo.isEmpty()) {
      return this;
    }
    if (this.inclusionInfo.isEmpty()) {
      this.inclusionInfo = inclusionInfo;
    } else {
      // Both are present, take the one with the smallest distance
      if (inclusionInfo.get().getDelay().isLessThan(this.inclusionInfo.get().getDelay())) {
        this.inclusionInfo = inclusionInfo;
      }
    }
    return this;
  }
}
