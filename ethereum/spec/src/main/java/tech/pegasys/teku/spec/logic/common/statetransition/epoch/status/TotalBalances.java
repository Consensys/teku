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

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;

public class TotalBalances {

  private final UInt64 currentEpochActiveValidators;
  private final UInt64 previousEpochActiveValidators;
  private final UInt64 currentEpochSourceAttesters;
  private final UInt64 currentEpochTargetAttesters;
  private final UInt64 previousEpochSourceAttesters;
  private final UInt64 previousEpochTargetAttesters;
  private final UInt64 previousEpochHeadAttesters;

  public TotalBalances(
      final SpecConfig specConfig,
      UInt64 currentEpochActiveValidators,
      UInt64 previousEpochActiveValidators,
      UInt64 currentEpochSourceAttesters,
      UInt64 currentEpochTargetAttesters,
      UInt64 previousEpochSourceAttesters,
      UInt64 previousEpochTargetAttesters,
      UInt64 previousEpochHeadAttesters) {
    UInt64 effectiveBalanceIncrement = specConfig.getEffectiveBalanceIncrement();
    this.currentEpochActiveValidators = currentEpochActiveValidators.max(effectiveBalanceIncrement);
    this.previousEpochActiveValidators =
        previousEpochActiveValidators.max(effectiveBalanceIncrement);
    this.currentEpochSourceAttesters = currentEpochSourceAttesters.max(effectiveBalanceIncrement);
    this.currentEpochTargetAttesters = currentEpochTargetAttesters.max(effectiveBalanceIncrement);
    this.previousEpochSourceAttesters = previousEpochSourceAttesters.max(effectiveBalanceIncrement);
    this.previousEpochTargetAttesters = previousEpochTargetAttesters.max(effectiveBalanceIncrement);
    this.previousEpochHeadAttesters = previousEpochHeadAttesters.max(effectiveBalanceIncrement);
  }

  /** @return The sum of effective balances of all active validators from the current epoch. */
  public UInt64 getCurrentEpochActiveValidators() {
    return currentEpochActiveValidators;
  }

  /** @return The sum of effective balances of all active validators from the previous epoch. */
  public UInt64 getPreviousEpochActiveValidators() {
    return previousEpochActiveValidators;
  }

  /**
   * @return The sum of effective balances of all attesters from the current epoch that attested to
   *     the correct source (justified checkpoint).
   */
  public UInt64 getCurrentEpochSourceAttesters() {
    return currentEpochSourceAttesters;
  }

  /**
   * @return The sum of effective balances of all attesters from the current epoch that attested to
   *     the correct target (epoch boundary block).
   */
  public UInt64 getCurrentEpochTargetAttesters() {
    return currentEpochTargetAttesters;
  }

  /**
   * @return The sum of effective balances of all attesters from the previous epoch that attested to
   *     the correct source (justified checkpoint).
   */
  public UInt64 getPreviousEpochSourceAttesters() {
    return previousEpochSourceAttesters;
  }

  /**
   * @return The sum of effective balances of all attesters from the previous epoch that attested to
   *     the correct target (epoch boundary block).
   */
  public UInt64 getPreviousEpochTargetAttesters() {
    return previousEpochTargetAttesters;
  }

  /**
   * @return The sum of effective balances of all attesters from the previous epoch that attested to
   *     the correct head block at their assigned slot.
   */
  public UInt64 getPreviousEpochHeadAttesters() {
    return previousEpochHeadAttesters;
  }
}
