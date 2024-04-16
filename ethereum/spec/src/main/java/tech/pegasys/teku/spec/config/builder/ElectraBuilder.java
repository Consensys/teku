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

package tech.pegasys.teku.spec.config.builder;

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigElectraImpl;

public class ElectraBuilder implements ForkConfigBuilder<SpecConfigDeneb, SpecConfigElectra> {

  private Bytes4 electraForkVersion;
  private UInt64 electraForkEpoch;
  // TODO: remove default when EIP-7251 become part of the Electra
  private UInt64 minPerEpochChurnLimitElectra = UInt64.ZERO;
  // TODO: remove default when EIP-7251 become part of the Electra
  private UInt64 maxPerEpochActivationExitChurnLimit = UInt64.ZERO;
  private Integer maxDepositReceiptsPerPayload;
  private Integer maxExecutionLayerExits;
  private UInt64 minActivationBalance;
  private UInt64 maxEffectiveBalanceElectra;
  private Integer pendingBalanceDepositsLimit;
  private Integer pendingPartialWithdrawalsLimit;
  private Integer pendingConsolidationsLimit;
  private Integer whistleblowerRewardQuotientElectra;
  private Integer minSlashingPenaltyQuotientElectra;
  private Integer maxPartialWithdrawalsPerPayload;
  private Integer maxAttesterSlashingsElectra;
  private Integer maxAttestationsElectra;
  private Integer maxConsolidations;
  private UInt64 fieldElementsPerCell;
  private Integer dataColumnSidecarSubnetCount;
  private Integer custodyRequirement;

  ElectraBuilder() {}

  @Override
  public SpecConfigElectra build(final SpecConfigDeneb specConfig) {
    return new SpecConfigElectraImpl(
        specConfig,
        electraForkVersion,
        electraForkEpoch,
        maxDepositReceiptsPerPayload,
        maxExecutionLayerExits,
        minPerEpochChurnLimitElectra,
        maxPerEpochActivationExitChurnLimit,
        minActivationBalance,
        maxEffectiveBalanceElectra,
        pendingBalanceDepositsLimit,
        pendingPartialWithdrawalsLimit,
        pendingConsolidationsLimit,
        whistleblowerRewardQuotientElectra,
        minSlashingPenaltyQuotientElectra,
        maxPartialWithdrawalsPerPayload,
        maxAttesterSlashingsElectra,
        maxAttestationsElectra,
        maxConsolidations,
        fieldElementsPerCell,
        dataColumnSidecarSubnetCount,
        custodyRequirement);
  }

  public ElectraBuilder electraForkEpoch(final UInt64 electraForkEpoch) {
    checkNotNull(electraForkEpoch);
    this.electraForkEpoch = electraForkEpoch;
    return this;
  }

  public ElectraBuilder electraForkVersion(final Bytes4 electraForkVersion) {
    checkNotNull(electraForkVersion);
    this.electraForkVersion = electraForkVersion;
    return this;
  }

  public ElectraBuilder maxDepositReceiptsPerPayload(final Integer maxDepositReceiptsPerPayload) {
    checkNotNull(maxDepositReceiptsPerPayload);
    this.maxDepositReceiptsPerPayload = maxDepositReceiptsPerPayload;
    return this;
  }

  public ElectraBuilder maxExecutionLayerExits(final Integer maxExecutionLayerExits) {
    checkNotNull(maxExecutionLayerExits);
    this.maxExecutionLayerExits = maxExecutionLayerExits;
    return this;
  }

  public ElectraBuilder fieldElementsPerCell(final UInt64 fieldElementsPerCell) {
    checkNotNull(fieldElementsPerCell);
    this.fieldElementsPerCell = fieldElementsPerCell;
    return this;
  }

  public ElectraBuilder minPerEpochChurnLimitElectra(final UInt64 minPerEpochChurnLimitElectra) {
    checkNotNull(minPerEpochChurnLimitElectra);
    this.minPerEpochChurnLimitElectra = minPerEpochChurnLimitElectra;
    return this;
  }

  public ElectraBuilder maxPerEpochActivationExitChurnLimit(
      final UInt64 maxPerEpochActivationExitChurnLimit) {
    checkNotNull(maxPerEpochActivationExitChurnLimit);
    this.maxPerEpochActivationExitChurnLimit = maxPerEpochActivationExitChurnLimit;
    return this;
  }

  public ElectraBuilder minActivationBalance(final UInt64 minActivationBalance) {
    checkNotNull(minActivationBalance);
    this.minActivationBalance = minActivationBalance;
    return this;
  }

  public ElectraBuilder maxEffectiveBalanceElectra(final UInt64 maxEffectiveBalanceElectra) {
    checkNotNull(maxEffectiveBalanceElectra);
    this.maxEffectiveBalanceElectra = maxEffectiveBalanceElectra;
    return this;
  }

  public ElectraBuilder pendingBalanceDepositsLimit(final Integer pendingBalanceDepositsLimit) {
    checkNotNull(pendingBalanceDepositsLimit);
    this.pendingBalanceDepositsLimit = pendingBalanceDepositsLimit;
    return this;
  }

  public ElectraBuilder pendingPartialWithdrawalsLimit(
      final Integer pendingPartialWithdrawalsLimit) {
    checkNotNull(pendingPartialWithdrawalsLimit);
    this.pendingPartialWithdrawalsLimit = pendingPartialWithdrawalsLimit;
    return this;
  }

  public ElectraBuilder pendingConsolidationsLimit(final Integer pendingConsolidationsLimit) {
    checkNotNull(pendingConsolidationsLimit);
    this.pendingConsolidationsLimit = pendingConsolidationsLimit;
    return this;
  }

  public ElectraBuilder whistleblowerRewardQuotientElectra(
      final Integer whistleblowerRewardQuotientElectra) {
    checkNotNull(whistleblowerRewardQuotientElectra);
    this.whistleblowerRewardQuotientElectra = whistleblowerRewardQuotientElectra;
    return this;
  }

  public ElectraBuilder minSlashingPenaltyQuotientElectra(
      final Integer minSlashingPenaltyQuotientElectra) {
    checkNotNull(minSlashingPenaltyQuotientElectra);
    this.minSlashingPenaltyQuotientElectra = minSlashingPenaltyQuotientElectra;
    return this;
  }

  public ElectraBuilder maxPartialWithdrawalsPerPayload(
      final Integer maxPartialWithdrawalsPerPayload) {
    checkNotNull(maxPartialWithdrawalsPerPayload);
    this.maxPartialWithdrawalsPerPayload = maxPartialWithdrawalsPerPayload;
    return this;
  }

  public ElectraBuilder maxAttesterSlashingsElectra(final Integer maxAttesterSlashingsElectra) {
    checkNotNull(maxAttesterSlashingsElectra);
    this.maxAttesterSlashingsElectra = maxAttesterSlashingsElectra;
    return this;
  }

  public ElectraBuilder maxAttestationsElectra(final Integer maxAttestationsElectra) {
    checkNotNull(maxAttestationsElectra);
    this.maxAttestationsElectra = maxAttestationsElectra;
    return this;
  }

  public ElectraBuilder maxConsolidations(final Integer maxConsolidations) {
    checkNotNull(maxConsolidations);
    this.maxConsolidations = maxConsolidations;
    return this;
  }

  public ElectraBuilder dataColumnSidecarSubnetCount(final Integer blobSidecarSubnetCount) {
    this.dataColumnSidecarSubnetCount = blobSidecarSubnetCount;
    return this;
  }

  public ElectraBuilder custodyRequirement(final Integer custodyRequirement) {
    checkNotNull(custodyRequirement);
    this.custodyRequirement = custodyRequirement;
    return this;
  }

  @Override
  public void validate() {
    if (electraForkEpoch == null) {
      electraForkEpoch = SpecConfig.FAR_FUTURE_EPOCH;
      electraForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
    }

    // Fill default zeros if fork is unsupported
    if (electraForkEpoch.equals(FAR_FUTURE_EPOCH)) {
      SpecBuilderUtil.fillMissingValuesWithZeros(this);
    }

    validateConstants();
  }

  @Override
  public Map<String, Object> getValidationMap() {
    final Map<String, Object> constants = new HashMap<>();

    constants.put("electraForkEpoch", electraForkEpoch);
    constants.put("electraForkVersion", electraForkVersion);
    constants.put("maxDepositReceiptsPerPayload", maxDepositReceiptsPerPayload);
    constants.put("minPerEpochChurnLimitElectra", minPerEpochChurnLimitElectra);
    constants.put("maxExecutionLayerExits", maxExecutionLayerExits);
    constants.put("minActivationBalance", minActivationBalance);
    constants.put("maxEffectiveBalanceElectra", maxEffectiveBalanceElectra);
    constants.put("pendingBalanceDepositsLimit", pendingBalanceDepositsLimit);
    constants.put("pendingPartialWithdrawalsLimit", pendingPartialWithdrawalsLimit);
    constants.put("pendingConsolidationsLimit", pendingConsolidationsLimit);
    constants.put("whistleblowerRewardQuotientElectra", whistleblowerRewardQuotientElectra);
    constants.put("minSlashingPenaltyQuotientElectra", minSlashingPenaltyQuotientElectra);
    constants.put("maxPartialWithdrawalsPerPayload", maxPartialWithdrawalsPerPayload);
    constants.put("maxAttesterSlashingsElectra", maxAttesterSlashingsElectra);
    constants.put("maxAttestationsElectra", maxAttestationsElectra);
    constants.put("maxConsolidations", maxConsolidations);
    constants.put("dataColumnSidecarSubnetCount", dataColumnSidecarSubnetCount);
    constants.put("custodyRequirement", custodyRequirement);

    return constants;
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {
    rawConfig.accept("ELECTRA_FORK_EPOCH", electraForkEpoch);
  }
}
