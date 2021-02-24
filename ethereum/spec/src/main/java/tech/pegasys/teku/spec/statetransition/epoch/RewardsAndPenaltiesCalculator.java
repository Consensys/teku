/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.statetransition.epoch;

import tech.pegasys.teku.independent.TotalBalances;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.statetransition.epoch.status.ValidatorStatus;

public interface RewardsAndPenaltiesCalculator {
  Deltas getAttestationDeltas() throws IllegalArgumentException;

  Deltas getDeltas(Step step) throws IllegalArgumentException;

  void applySourceDelta(
      ValidatorStatus validator,
      UInt64 baseReward,
      TotalBalances totalBalances,
      UInt64 finalityDelay,
      Deltas.Delta delta);

  void applyTargetDelta(
      ValidatorStatus validator,
      UInt64 baseReward,
      TotalBalances totalBalances,
      UInt64 finalityDelay,
      Deltas.Delta delta);

  void applyHeadDelta(
      ValidatorStatus validator,
      UInt64 baseReward,
      TotalBalances totalBalances,
      UInt64 finalityDelay,
      Deltas.Delta delta);

  void applyInclusionDelayDelta(
      ValidatorStatus validator, UInt64 baseReward, Deltas.Delta delta, Deltas deltas);

  void applyInactivityPenaltyDelta(
      ValidatorStatus validator, UInt64 baseReward, UInt64 finalityDelay, Deltas.Delta delta);

  void applyAttestationComponentDelta(
      boolean indexInUnslashedAttestingIndices,
      UInt64 attestingBalance,
      TotalBalances totalBalances,
      UInt64 baseReward,
      UInt64 finalityDelay,
      Deltas.Delta delta);

  interface Step {
    void apply(
        final Deltas deltas,
        final TotalBalances totalBalances,
        final UInt64 finalityDelay,
        final ValidatorStatus validator,
        final UInt64 baseReward,
        final Deltas.Delta delta);
  }
}
