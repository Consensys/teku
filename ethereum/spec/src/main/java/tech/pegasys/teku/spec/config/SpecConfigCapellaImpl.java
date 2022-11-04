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

package tech.pegasys.teku.spec.config;

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SpecConfigCapellaImpl extends DelegatingSpecConfigBellatrix
    implements SpecConfigCapella {

  private final Bytes4 capellaForkVersion;
  private final UInt64 capellaForkEpoch;

  private final UInt64 maxPartialWithdrawalsPerEpoch;

  private final UInt64 withdrawalQueueLimit;

  private final UInt64 maxBlsToExecutionChanges;

  private final UInt64 maxWithdrawalsPerPayload;

  public SpecConfigCapellaImpl(
      final SpecConfigBellatrix specConfig,
      final Bytes4 capellaForkVersion,
      final UInt64 capellaForkEpoch,
      final UInt64 maxPartialWithdrawalsPerEpoch,
      final UInt64 withdrawalQueueLimit,
      final UInt64 maxBlsToExecutionChanges,
      final UInt64 maxWithdrawalsPerPayload) {
    super(specConfig);
    this.capellaForkVersion = capellaForkVersion;
    this.capellaForkEpoch = capellaForkEpoch;
    this.maxPartialWithdrawalsPerEpoch = maxPartialWithdrawalsPerEpoch;
    this.withdrawalQueueLimit = withdrawalQueueLimit;
    this.maxBlsToExecutionChanges = maxBlsToExecutionChanges;
    this.maxWithdrawalsPerPayload = maxWithdrawalsPerPayload;
  }

  @Override
  public Bytes4 getCapellaForkVersion() {
    return capellaForkVersion;
  }

  @Override
  public UInt64 getCapellaForkEpoch() {
    return capellaForkEpoch;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SpecConfigCapellaImpl that = (SpecConfigCapellaImpl) o;
    return Objects.equals(specConfig, that.specConfig)
        && Objects.equals(capellaForkVersion, that.capellaForkVersion)
        && Objects.equals(capellaForkEpoch, that.capellaForkEpoch)
        && Objects.equals(maxPartialWithdrawalsPerEpoch, that.maxPartialWithdrawalsPerEpoch)
        && Objects.equals(withdrawalQueueLimit, that.withdrawalQueueLimit)
        && Objects.equals(maxBlsToExecutionChanges, that.maxBlsToExecutionChanges)
        && Objects.equals(maxWithdrawalsPerPayload, that.maxWithdrawalsPerPayload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        specConfig,
        capellaForkVersion,
        capellaForkEpoch,
        maxPartialWithdrawalsPerEpoch,
        withdrawalQueueLimit,
        maxBlsToExecutionChanges,
        maxWithdrawalsPerPayload);
  }

  @Override
  public UInt64 getMaxPartialWithdrawalsPerEpoch() {
    return maxPartialWithdrawalsPerEpoch;
  }

  @Override
  public UInt64 getWithdrawalQueueLimit() {
    return withdrawalQueueLimit;
  }

  @Override
  public UInt64 getMaxBlsToExecutionChanges() {
    return maxBlsToExecutionChanges;
  }

  @Override
  public UInt64 getMaxWithdrawalsPerPayload() {
    return maxWithdrawalsPerPayload;
  }

  @Override
  public Optional<SpecConfigCapella> toVersionCapella() {
    return Optional.of(this);
  }
}
