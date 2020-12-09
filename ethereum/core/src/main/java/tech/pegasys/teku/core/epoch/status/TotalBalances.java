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

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

import java.util.List;
import java.util.Optional;

import static tech.pegasys.teku.util.config.Constants.EFFECTIVE_BALANCE_INCREMENT;

public class TotalBalances {

  public static volatile Optional<TotalBalances> latestTotalBalances = Optional.empty();

  private UInt64 currentEpoch = UInt64.ZERO;
  private UInt64 previousEpoch = UInt64.ZERO;
  private UInt64 currentEpochAttesters = UInt64.ZERO;
  private UInt64 currentEpochTargetAttesters = UInt64.ZERO;
  private UInt64 previousEpochAttesters = UInt64.ZERO;
  private UInt64 previousEpochTargetAttesters = UInt64.ZERO;
  private UInt64 previousEpochHeadAttesters = UInt64.ZERO;

  private TotalBalances() {}

  public static TotalBalances create(final List<ValidatorStatus> statuses) {
    final TotalBalances totalBalances = new TotalBalances();
    for (ValidatorStatus status : statuses) {
      final UInt64 balance = status.getCurrentEpochEffectiveBalance();
      if (status.isActiveInCurrentEpoch()) {
        totalBalances.currentEpoch = totalBalances.currentEpoch.plus(balance);
      }
      if (status.isActiveInPreviousEpoch()) {
        totalBalances.previousEpoch = totalBalances.previousEpoch.plus(balance);
      }

      if (status.isSlashed()) {
        continue;
      }
      if (status.isCurrentEpochAttester()) {
        totalBalances.currentEpochAttesters = totalBalances.currentEpochAttesters.plus(balance);

        if (status.isCurrentEpochTargetAttester()) {
          totalBalances.currentEpochTargetAttesters =
              totalBalances.currentEpochTargetAttesters.plus(balance);
        }
      }

      if (status.isPreviousEpochAttester()) {
        totalBalances.previousEpochAttesters = totalBalances.previousEpochAttesters.plus(balance);
        if (status.isPreviousEpochTargetAttester()) {
          totalBalances.previousEpochTargetAttesters =
              totalBalances.previousEpochTargetAttesters.plus(balance);
        }
        if (status.isPreviousEpochHeadAttester()) {
          totalBalances.previousEpochHeadAttesters =
              totalBalances.previousEpochHeadAttesters.plus(balance);
        }
      }
    }
    return totalBalances;
  }

  public UInt64 getCurrentEpoch() {
    return currentEpoch.max(EFFECTIVE_BALANCE_INCREMENT);
  }

  public UInt64 getPreviousEpoch() {
    return previousEpoch.max(EFFECTIVE_BALANCE_INCREMENT);
  }

  public UInt64 getCurrentEpochAttesters() {
    return currentEpochAttesters.max(EFFECTIVE_BALANCE_INCREMENT);
  }

  public UInt64 getCurrentEpochTargetAttesters() {
    return currentEpochTargetAttesters.max(EFFECTIVE_BALANCE_INCREMENT);
  }

  public UInt64 getPreviousEpochAttesters() {
    return previousEpochAttesters.max(EFFECTIVE_BALANCE_INCREMENT);
  }

  public UInt64 getPreviousEpochTargetAttesters() {
    return previousEpochTargetAttesters.max(EFFECTIVE_BALANCE_INCREMENT);
  }

  public UInt64 getPreviousEpochHeadAttesters() {
    return previousEpochHeadAttesters.max(EFFECTIVE_BALANCE_INCREMENT);
  }
}
