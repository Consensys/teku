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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface SpecConfigElectra extends SpecConfigDeneb, NetworkingSpecConfigElectra {

  UInt64 UNSET_DEPOSIT_RECEIPTS_START_INDEX = UInt64.MAX_VALUE;

  static SpecConfigElectra required(final SpecConfig specConfig) {
    return specConfig
        .toVersionElectra()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Electra spec config but got: "
                        + specConfig.getClass().getSimpleName()));
  }

  UInt64 getMinActivationBalance();

  UInt64 getMaxEffectiveBalanceElectra();

  int getPendingBalanceDepositsLimit();

  int getPendingPartialWithdrawalsLimit();

  int getPendingConsolidationsLimit();

  int getWhistleblowerRewardQuotientElectra();

  int getMinSlashingPenaltyQuotientElectra();

  int getMaxAttesterSlashingsElectra();

  int getMaxAttestationsElectra();

  int getMaxConsolidations();

  int getMaxPartialWithdrawalsPerPayload();

  UInt64 getMinPerEpochChurnLimitElectra();

  UInt64 getMaxPerEpochActivationExitChurnLimit();

  Bytes4 getElectraForkVersion();

  UInt64 getElectraForkEpoch();

  int getMaxDepositReceiptsPerPayload();

  int getMaxExecutionLayerExits();

  UInt64 getFieldElementsPerCell();

  default UInt64 getFieldElementsPerExtendedBlob() {
    return UInt64.valueOf(getFieldElementsPerBlob()).times(2);
  }

  default UInt64 getNumberOfColumns() {
    return getFieldElementsPerExtendedBlob().dividedBy(getFieldElementsPerCell());
  }

  @Override
  Optional<SpecConfigElectra> toVersionElectra();
}
