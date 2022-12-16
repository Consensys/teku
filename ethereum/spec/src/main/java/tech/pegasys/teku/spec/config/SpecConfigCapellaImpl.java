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

  private final int maxBlsToExecutionChanges;
  private final int maxWithdrawalsPerPayload;
  private final int maxValidatorsPerWithdrawalSweep;

  public SpecConfigCapellaImpl(
      final SpecConfigBellatrix specConfig,
      final Bytes4 capellaForkVersion,
      final UInt64 capellaForkEpoch,
      final int maxBlsToExecutionChanges,
      final int maxWithdrawalsPerPayload,
      final int maxValidatorsPerWithdrawalSweep) {
    super(specConfig);
    this.capellaForkVersion = capellaForkVersion;
    this.capellaForkEpoch = capellaForkEpoch;
    this.maxBlsToExecutionChanges = maxBlsToExecutionChanges;
    this.maxWithdrawalsPerPayload = maxWithdrawalsPerPayload;
    this.maxValidatorsPerWithdrawalSweep = maxValidatorsPerWithdrawalSweep;
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
        && maxBlsToExecutionChanges == that.maxBlsToExecutionChanges
        && maxWithdrawalsPerPayload == that.maxWithdrawalsPerPayload
        && maxValidatorsPerWithdrawalSweep == that.maxValidatorsPerWithdrawalSweep;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        specConfig,
        capellaForkVersion,
        capellaForkEpoch,
        maxBlsToExecutionChanges,
        maxWithdrawalsPerPayload,
        maxValidatorsPerWithdrawalSweep);
  }

  @Override
  public int getMaxBlsToExecutionChanges() {
    return maxBlsToExecutionChanges;
  }

  @Override
  public int getMaxWithdrawalsPerPayload() {
    return maxWithdrawalsPerPayload;
  }

  @Override
  public int getMaxValidatorsPerWithdrawalSweep() {
    return maxValidatorsPerWithdrawalSweep;
  }

  @Override
  public Optional<SpecConfigCapella> toVersionCapella() {
    return Optional.of(this);
  }
}
