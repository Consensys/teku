/*
 * Copyright Consensys Software Inc., 2022
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
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface SpecConfigBellatrix extends SpecConfigAltair {

  static SpecConfigBellatrix required(final SpecConfig specConfig) {
    return specConfig
        .toVersionBellatrix()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected bellatrix spec config but got: "
                        + specConfig.getClass().getSimpleName()));
  }

  @Override
  Optional<SpecConfigBellatrix> toVersionBellatrix();

  Bytes4 getBellatrixForkVersion();

  UInt64 getBellatrixForkEpoch();

  UInt64 getInactivityPenaltyQuotientBellatrix();

  int getMinSlashingPenaltyQuotientBellatrix();

  int getProportionalSlashingMultiplierBellatrix();

  int getMaxBytesPerTransaction();

  int getMaxTransactionsPerPayload();

  int getBytesPerLogsBloom();

  int getMaxExtraDataBytes();

  UInt64 getTerminalBlockHashActivationEpoch();

  Bytes32 getTerminalBlockHash();

  UInt256 getTerminalTotalDifficulty();

  int getSafeSlotsToImportOptimistically();
}
