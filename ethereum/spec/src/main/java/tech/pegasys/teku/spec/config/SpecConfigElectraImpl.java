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

  private final UInt64 minActivationBalance;
  private final UInt64 maxEffectiveBalanceElectra;
  private final int pendingDepositsLimit;
  private final int pendingPartialWithdrawalsLimit;
  private final int pendingConsolidationsLimit;
  private final int minSlashingPenaltyQuotientElectra;
  private final int whistleblowerRewardQuotientElectra;
  private final int maxAttesterSlashingsElectra;
  private final int maxAttestationsElectra;
  private final int maxConsolidationRequestsPerPayload;
  private final int maxDepositRequestsPerPayload;
  private final int maxWithdrawalRequestsPerPayload;
  private final int maxPendingPartialsPerWithdrawalsSweep;
  private final int maxPendingDepositsPerEpoch;

  public SpecConfigElectraImpl(
      final SpecConfigDeneb specConfig,
      final Bytes4 electraForkVersion,
      final UInt64 electraForkEpoch,
      final UInt64 minPerEpochChurnLimitElectra,
      final UInt64 minActivationBalance,
      final UInt64 maxEffectiveBalanceElectra,
      final int pendingDepositsLimit,
      final int pendingPartialWithdrawalsLimit,
      final int pendingConsolidationsLimit,
      final int minSlashingPenaltyQuotientElectra,
      final int whistleblowerRewardQuotientElectra,
      final int maxAttesterSlashingsElectra,
      final int maxAttestationsElectra,
      final int maxConsolidationRequestsPerPayload,
      final int maxDepositRequestsPerPayload,
      final int maxWithdrawalRequestsPerPayload,
      final int maxPendingPartialsPerWithdrawalsSweep,
      final int maxPendingDepositsPerEpoch) {
    super(specConfig);
    this.electraForkVersion = electraForkVersion;
    this.electraForkEpoch = electraForkEpoch;
    this.minPerEpochChurnLimitElectra = minPerEpochChurnLimitElectra;
    this.minActivationBalance = minActivationBalance;
    this.maxEffectiveBalanceElectra = maxEffectiveBalanceElectra;
    this.pendingDepositsLimit = pendingDepositsLimit;
    this.pendingPartialWithdrawalsLimit = pendingPartialWithdrawalsLimit;
    this.pendingConsolidationsLimit = pendingConsolidationsLimit;
    this.minSlashingPenaltyQuotientElectra = minSlashingPenaltyQuotientElectra;
    this.whistleblowerRewardQuotientElectra = whistleblowerRewardQuotientElectra;
    this.maxAttesterSlashingsElectra = maxAttesterSlashingsElectra;
    this.maxAttestationsElectra = maxAttestationsElectra;
    this.maxConsolidationRequestsPerPayload = maxConsolidationRequestsPerPayload;
    this.maxDepositRequestsPerPayload = maxDepositRequestsPerPayload;
    this.maxWithdrawalRequestsPerPayload = maxWithdrawalRequestsPerPayload;
    this.maxPendingPartialsPerWithdrawalsSweep = maxPendingPartialsPerWithdrawalsSweep;
    this.maxPendingDepositsPerEpoch = maxPendingDepositsPerEpoch;
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
  public UInt64 getMinPerEpochChurnLimitElectra() {
    return minPerEpochChurnLimitElectra;
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
  public int getPendingDepositsLimit() {
    return pendingDepositsLimit;
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
  public int getMinSlashingPenaltyQuotientElectra() {
    return minSlashingPenaltyQuotientElectra;
  }

  @Override
  public int getWhistleblowerRewardQuotientElectra() {
    return whistleblowerRewardQuotientElectra;
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
  public int getMaxConsolidationRequestsPerPayload() {
    return maxConsolidationRequestsPerPayload;
  }

  @Override
  public int getMaxDepositRequestsPerPayload() {
    return maxDepositRequestsPerPayload;
  }

  @Override
  public int getMaxWithdrawalRequestsPerPayload() {
    return maxWithdrawalRequestsPerPayload;
  }

  @Override
  public int getMaxPendingPartialsPerWithdrawalsSweep() {
    return maxPendingPartialsPerWithdrawalsSweep;
  }

  @Override
  public int getMaxPendingDepositsPerEpoch() {
    return maxPendingDepositsPerEpoch;
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
        && Objects.equals(minPerEpochChurnLimitElectra, that.minPerEpochChurnLimitElectra)
        && Objects.equals(minActivationBalance, that.minActivationBalance)
        && Objects.equals(maxEffectiveBalanceElectra, that.maxEffectiveBalanceElectra)
        && pendingDepositsLimit == that.pendingDepositsLimit
        && pendingPartialWithdrawalsLimit == that.pendingPartialWithdrawalsLimit
        && pendingConsolidationsLimit == that.pendingConsolidationsLimit
        && minSlashingPenaltyQuotientElectra == that.minSlashingPenaltyQuotientElectra
        && whistleblowerRewardQuotientElectra == that.whistleblowerRewardQuotientElectra
        && maxAttesterSlashingsElectra == that.maxAttesterSlashingsElectra
        && maxAttestationsElectra == that.maxAttestationsElectra
        && maxConsolidationRequestsPerPayload == that.maxConsolidationRequestsPerPayload
        && maxDepositRequestsPerPayload == that.maxDepositRequestsPerPayload
        && maxWithdrawalRequestsPerPayload == that.maxWithdrawalRequestsPerPayload
        && maxPendingPartialsPerWithdrawalsSweep == that.maxPendingPartialsPerWithdrawalsSweep
        && maxPendingDepositsPerEpoch == that.maxPendingDepositsPerEpoch;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        specConfig,
        electraForkVersion,
        electraForkEpoch,
        minPerEpochChurnLimitElectra,
        minActivationBalance,
        maxEffectiveBalanceElectra,
        pendingDepositsLimit,
        pendingPartialWithdrawalsLimit,
        pendingConsolidationsLimit,
        minSlashingPenaltyQuotientElectra,
        whistleblowerRewardQuotientElectra,
        maxAttesterSlashingsElectra,
        maxAttestationsElectra,
        maxConsolidationRequestsPerPayload,
        maxDepositRequestsPerPayload,
        maxWithdrawalRequestsPerPayload,
        maxPendingPartialsPerWithdrawalsSweep,
        maxPendingDepositsPerEpoch);
  }
}
