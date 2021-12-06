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

package tech.pegasys.teku.spec.config;

import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SpecConfigMerge extends DelegatingSpecConfigAltair {

  // Fork
  private final Bytes4 mergeForkVersion;
  private final UInt64 mergeForkEpoch;
  private final UInt64 inactivityPenaltyQuotientMerge;
  private final int minSlashingPenaltyQuotientMerge;
  private final int proportionalSlashingMultiplierMerge;
  private final int maxBytesPerTransaction;
  private final int maxTransactionsPerPayload;
  private final int bytesPerLogsBloom;
  private final int maxExtraDataBytes;

  // Transition
  private final UInt256 terminalTotalDifficulty;
  private final Bytes32 terminalBlockHash;
  private final UInt64 terminalBlockHashActivationEpoch;

  public SpecConfigMerge(
      final SpecConfigAltair specConfig,
      final Bytes4 mergeForkVersion,
      final UInt64 mergeForkEpoch,
      final UInt64 inactivityPenaltyQuotientMerge,
      final int minSlashingPenaltyQuotientMerge,
      final int proportionalSlashingMultiplierMerge,
      final int maxBytesPerTransaction,
      final int maxTransactionsPerPayload,
      final int bytesPerLogsBloom,
      final int maxExtraDataBytes,
      final UInt256 terminalTotalDifficulty,
      final Bytes32 terminalBlockHash,
      final UInt64 terminalBlockHashActivationEpoch) {
    super(specConfig);
    this.mergeForkVersion = mergeForkVersion;
    this.mergeForkEpoch = mergeForkEpoch;
    this.inactivityPenaltyQuotientMerge = inactivityPenaltyQuotientMerge;
    this.minSlashingPenaltyQuotientMerge = minSlashingPenaltyQuotientMerge;
    this.proportionalSlashingMultiplierMerge = proportionalSlashingMultiplierMerge;
    this.maxBytesPerTransaction = maxBytesPerTransaction;
    this.maxTransactionsPerPayload = maxTransactionsPerPayload;
    this.bytesPerLogsBloom = bytesPerLogsBloom;
    this.maxExtraDataBytes = maxExtraDataBytes;
    this.terminalTotalDifficulty = terminalTotalDifficulty;
    this.terminalBlockHash = terminalBlockHash;
    this.terminalBlockHashActivationEpoch = terminalBlockHashActivationEpoch;
  }

  public static SpecConfigMerge required(final SpecConfig specConfig) {
    return specConfig
        .toVersionMerge()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected merge spec config but got: "
                        + specConfig.getClass().getSimpleName()));
  }

  public Bytes4 getMergeForkVersion() {
    return mergeForkVersion;
  }

  public UInt64 getMergeForkEpoch() {
    return mergeForkEpoch;
  }

  public UInt64 getInactivityPenaltyQuotientMerge() {
    return inactivityPenaltyQuotientMerge;
  }

  public int getMinSlashingPenaltyQuotientMerge() {
    return minSlashingPenaltyQuotientMerge;
  }

  public int getProportionalSlashingMultiplierMerge() {
    return proportionalSlashingMultiplierMerge;
  }

  public int getMaxBytesPerTransaction() {
    return maxBytesPerTransaction;
  }

  public int getMaxTransactionsPerPayload() {
    return maxTransactionsPerPayload;
  }

  public int getBytesPerLogsBloom() {
    return bytesPerLogsBloom;
  }

  public int getMaxExtraDataBytes() {
    return maxExtraDataBytes;
  }

  public UInt256 getTerminalTotalDifficulty() {
    return terminalTotalDifficulty;
  }

  public Bytes32 getTerminalBlockHash() {
    return terminalBlockHash;
  }

  public UInt64 getTerminalBlockHashActivationEpoch() {
    return terminalBlockHashActivationEpoch;
  }

  @Override
  public Optional<SpecConfigMerge> toVersionMerge() {
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
    final SpecConfigMerge that = (SpecConfigMerge) o;
    return Objects.equals(specConfig, that.specConfig)
        && minSlashingPenaltyQuotientMerge == that.minSlashingPenaltyQuotientMerge
        && proportionalSlashingMultiplierMerge == that.proportionalSlashingMultiplierMerge
        && maxBytesPerTransaction == that.maxBytesPerTransaction
        && maxTransactionsPerPayload == that.maxTransactionsPerPayload
        && bytesPerLogsBloom == that.bytesPerLogsBloom
        && maxExtraDataBytes == that.maxExtraDataBytes
        && Objects.equals(mergeForkVersion, that.mergeForkVersion)
        && Objects.equals(mergeForkEpoch, that.mergeForkEpoch)
        && Objects.equals(inactivityPenaltyQuotientMerge, that.inactivityPenaltyQuotientMerge)
        && Objects.equals(terminalTotalDifficulty, that.terminalTotalDifficulty)
        && Objects.equals(terminalBlockHash, that.terminalBlockHash)
        && Objects.equals(terminalBlockHashActivationEpoch, that.terminalBlockHashActivationEpoch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        specConfig,
        mergeForkVersion,
        mergeForkEpoch,
        inactivityPenaltyQuotientMerge,
        minSlashingPenaltyQuotientMerge,
        proportionalSlashingMultiplierMerge,
        maxBytesPerTransaction,
        maxTransactionsPerPayload,
        bytesPerLogsBloom,
        maxExtraDataBytes,
        terminalTotalDifficulty,
        terminalBlockHash,
        terminalBlockHashActivationEpoch);
  }
}
