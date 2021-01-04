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

package tech.pegasys.teku.independent;

import static tech.pegasys.teku.util.config.Constants.EFFECTIVE_BALANCE_INCREMENT;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class TotalBalances {

  private final UInt64 currentEpoch;
  private final UInt64 previousEpoch;
  private final UInt64 currentEpochAttesters;
  private final UInt64 currentEpochTargetAttesters;
  private final UInt64 previousEpochAttesters;
  private final UInt64 previousEpochTargetAttesters;
  private final UInt64 previousEpochHeadAttesters;

  public TotalBalances(
      UInt64 currentEpoch,
      UInt64 previousEpoch,
      UInt64 currentEpochAttesters,
      UInt64 currentEpochTargetAttesters,
      UInt64 previousEpochAttesters,
      UInt64 previousEpochTargetAttesters,
      UInt64 previousEpochHeadAttesters) {
    this.currentEpoch = currentEpoch;
    this.previousEpoch = previousEpoch;
    this.currentEpochAttesters = currentEpochAttesters;
    this.currentEpochTargetAttesters = currentEpochTargetAttesters;
    this.previousEpochAttesters = previousEpochAttesters;
    this.previousEpochTargetAttesters = previousEpochTargetAttesters;
    this.previousEpochHeadAttesters = previousEpochHeadAttesters;
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
