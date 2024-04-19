/*
 * Copyright Consensys Software Inc., 2024
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

public class SpecConfigElectraImpl extends DelegatingSpecConfigDeneb implements SpecConfigElectra {

  private final Bytes4 electraForkVersion;
  private final UInt64 electraForkEpoch;
  private final UInt64 minPerEpochChurnLimitElectra;
  private final UInt64 maxPerEpochActivationExitChurnLimit;

  private final int maxDepositReceiptsPerPayload;
  private final int maxExecutionLayerExits;
  private final UInt64 minActivationBalance;
  private final UInt64 maxEffectiveBalanceElectra;
  private final int pendingBalanceDepositsLimit;
  private final int pendingPartialWithdrawalsLimit;
  private final int pendingConsolidationsLimit;
  private final int whistleblowerRewardQuotientElectra;
  private final int minSlashingPenaltyQuotientElectra;
  private final int maxPartialWithdrawalsPerPayload;
  private final int maxAttesterSlashingsElectra;
  private final int maxAttestationsElectra;
  private final int maxConsolidations;
  private final int dataColumnSidecarSubnetCount;
  private final int custodyRequirement;
  private final UInt64 fieldElementsPerCell;
  private final int minEpochsForDataColumnSidecarsRequests;

  public SpecConfigElectraImpl(
      final SpecConfigDeneb specConfig,
      final Bytes4 electraForkVersion,
      final UInt64 electraForkEpoch,
      final int maxDepositReceiptsPerPayload,
      final int maxExecutionLayerExits,
      final UInt64 minPerEpochChurnLimitElectra,
      final UInt64 maxPerEpochActivationExitChurnLimit,
      final UInt64 minActivationBalance,
      final UInt64 maxEffectiveBalanceElectra,
      final int pendingBalanceDepositsLimit,
      final int pendingPartialWithdrawalsLimit,
      final int pendingConsolidationsLimit,
      final int whistleblowerRewardQuotientElectra,
      final int minSlashingPenaltyQuotientElectra,
      final int maxPartialWithdrawalsPerPayload,
      final int maxAttesterSlashingsElectra,
      final int maxAttestationsElectra,
      final int maxConsolidations,
      final UInt64 fieldElementsPerCell,
      final int dataColumnSidecarSubnetCount,
      final int custodyRequirement,
      int minEpochsForDataColumnSidecarsRequests) {
    super(specConfig);
    this.electraForkVersion = electraForkVersion;
    this.electraForkEpoch = electraForkEpoch;
    this.maxDepositReceiptsPerPayload = maxDepositReceiptsPerPayload;
    this.maxExecutionLayerExits = maxExecutionLayerExits;
    this.minPerEpochChurnLimitElectra = minPerEpochChurnLimitElectra;
    this.maxPerEpochActivationExitChurnLimit = maxPerEpochActivationExitChurnLimit;
    this.minActivationBalance = minActivationBalance;
    this.maxEffectiveBalanceElectra = maxEffectiveBalanceElectra;
    this.pendingBalanceDepositsLimit = pendingBalanceDepositsLimit;
    this.pendingPartialWithdrawalsLimit = pendingPartialWithdrawalsLimit;
    this.pendingConsolidationsLimit = pendingConsolidationsLimit;
    this.whistleblowerRewardQuotientElectra = whistleblowerRewardQuotientElectra;
    this.minSlashingPenaltyQuotientElectra = minSlashingPenaltyQuotientElectra;
    this.maxPartialWithdrawalsPerPayload = maxPartialWithdrawalsPerPayload;
    this.maxAttesterSlashingsElectra = maxAttesterSlashingsElectra;
    this.maxAttestationsElectra = maxAttestationsElectra;
    this.maxConsolidations = maxConsolidations;
    this.fieldElementsPerCell = fieldElementsPerCell;
    this.dataColumnSidecarSubnetCount = dataColumnSidecarSubnetCount;
    this.custodyRequirement = custodyRequirement;
    this.minEpochsForDataColumnSidecarsRequests = minEpochsForDataColumnSidecarsRequests;
  }

  @Override
  public Bytes4 getElectraForkVersion() {
    return electraForkVersion;
  }

  @Override
  public UInt64 getElectraForkEpoch() {
    return electraForkEpoch;
  }

  @Override
  public int getMaxDepositReceiptsPerPayload() {
    return maxDepositReceiptsPerPayload;
  }

  @Override
  public int getMaxExecutionLayerExits() {
    return maxExecutionLayerExits;
  }

  @Override
  public UInt64 getMinActivationBalance() {
    return minActivationBalance;
  }

  @Override
  public UInt64 getMaxEffectiveBalanceElectra() {
    return maxEffectiveBalanceElectra;
  }

  @Override
  public int getPendingBalanceDepositsLimit() {
    return pendingBalanceDepositsLimit;
  }

  @Override
  public int getPendingPartialWithdrawalsLimit() {
    return pendingPartialWithdrawalsLimit;
  }

  @Override
  public int getPendingConsolidationsLimit() {
    return pendingConsolidationsLimit;
  }

  @Override
  public int getWhistleblowerRewardQuotientElectra() {
    return whistleblowerRewardQuotientElectra;
  }

  @Override
  public int getMinSlashingPenaltyQuotientElectra() {
    return minSlashingPenaltyQuotientElectra;
  }

  @Override
  public int getMaxAttesterSlashingsElectra() {
    return maxAttesterSlashingsElectra;
  }

  @Override
  public int getMaxAttestationsElectra() {
    return maxAttestationsElectra;
  }

  @Override
  public int getMaxConsolidations() {
    return maxConsolidations;
  }

  @Override
  public int getMaxPartialWithdrawalsPerPayload() {
    return maxPartialWithdrawalsPerPayload;
  }

  @Override
  public UInt64 getMinPerEpochChurnLimitElectra() {
    return minPerEpochChurnLimitElectra;
  }

  @Override
  public UInt64 getMaxPerEpochActivationExitChurnLimit() {
    return maxPerEpochActivationExitChurnLimit;
  }

  @Override
  public UInt64 getFieldElementsPerCell() {
    return fieldElementsPerCell;
  }

  @Override
  public int getDataColumnSidecarSubnetCount() {
    return dataColumnSidecarSubnetCount;
  }

  @Override
  public int getCustodyRequirement() {
    return custodyRequirement;
  }

  @Override
  public int getMinEpochsForDataColumnSidecarsRequests() {
    return minEpochsForDataColumnSidecarsRequests;
  }

  @Override
  public Optional<SpecConfigElectra> toVersionElectra() {
    return Optional.of(this);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SpecConfigElectraImpl that = (SpecConfigElectraImpl) o;
    return Objects.equals(specConfig, that.specConfig)
        && Objects.equals(electraForkVersion, that.electraForkVersion)
        && Objects.equals(electraForkEpoch, that.electraForkEpoch)
        && maxDepositReceiptsPerPayload == that.maxDepositReceiptsPerPayload
        && maxExecutionLayerExits == that.maxExecutionLayerExits;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        specConfig,
        electraForkVersion,
        electraForkEpoch,
        maxDepositReceiptsPerPayload,
        maxExecutionLayerExits);
  }
}
