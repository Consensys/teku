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

package tech.pegasys.teku.spec.config.builder;

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.spec.constants.NetworkConstants.DEFAULT_SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY;

import java.math.BigInteger;
import java.util.function.BiConsumer;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigBellatrixImpl;

public class BellatrixBuilder implements ForkConfigBuilder<SpecConfigAltair, SpecConfigBellatrix> {

  // Fork
  private Bytes4 bellatrixForkVersion;
  private UInt64 bellatrixForkEpoch;
  private UInt64 inactivityPenaltyQuotientBellatrix;
  private Integer minSlashingPenaltyQuotientBellatrix;
  private Integer proportionalSlashingMultiplierBellatrix;
  private Integer maxBytesPerTransaction;
  private Integer maxTransactionsPerPayload;
  private Integer bytesPerLogsBloom;
  private Integer maxExtraDataBytes;

  // Transition
  private UInt256 terminalTotalDifficulty;
  private Bytes32 terminalBlockHash;
  private UInt64 terminalBlockHashActivationEpoch;

  // Optimistic Sync
  private int safeSlotsToImportOptimistically = DEFAULT_SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY;

  BellatrixBuilder() {}

  @Override
  public SpecConfigBellatrix build(final SpecConfigAltair specConfig) {
    return new SpecConfigBellatrixImpl(
        specConfig,
        bellatrixForkVersion,
        bellatrixForkEpoch,
        inactivityPenaltyQuotientBellatrix,
        minSlashingPenaltyQuotientBellatrix,
        proportionalSlashingMultiplierBellatrix,
        maxBytesPerTransaction,
        maxTransactionsPerPayload,
        bytesPerLogsBloom,
        maxExtraDataBytes,
        terminalTotalDifficulty,
        terminalBlockHash,
        terminalBlockHashActivationEpoch,
        safeSlotsToImportOptimistically);
  }

  @Override
  public void validate() {
    if (bellatrixForkEpoch == null) {
      bellatrixForkEpoch = SpecConfig.FAR_FUTURE_EPOCH;
      bellatrixForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
    }
    SpecBuilderUtil.validateConstant("bellatrixForkVersion", bellatrixForkVersion);
    SpecBuilderUtil.validateConstant("bellatrixForkEpoch", bellatrixForkEpoch);
    SpecBuilderUtil.validateConstant(
        "inactivityPenaltyQuotientBellatrix", inactivityPenaltyQuotientBellatrix);
    SpecBuilderUtil.validateConstant(
        "minSlashingPenaltyQuotientBellatrix", minSlashingPenaltyQuotientBellatrix);
    SpecBuilderUtil.validateConstant(
        "proportionalSlashingMultiplierBellatrix", proportionalSlashingMultiplierBellatrix);
    SpecBuilderUtil.validateConstant("maxBytesPerTransaction", maxBytesPerTransaction);
    SpecBuilderUtil.validateConstant("maxTransactionsPerPayload", maxTransactionsPerPayload);
    SpecBuilderUtil.validateConstant("bytesPerLogsBloom", bytesPerLogsBloom);
    SpecBuilderUtil.validateConstant("maxExtraDataBytes", maxExtraDataBytes);

    // temporary, provide default values for backward compatibility
    if (terminalTotalDifficulty == null) {
      terminalTotalDifficulty =
          UInt256.valueOf(
              new BigInteger(
                  "115792089237316195423570985008687907853269984665640564039457584007913129638912"));
    }
    if (terminalBlockHash == null) {
      terminalBlockHash = Bytes32.fromHexStringLenient("0x00");
    }
    if (terminalBlockHashActivationEpoch == null) {
      terminalBlockHashActivationEpoch = UInt64.valueOf("18446744073709551615");
    }
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {
    rawConfig.accept("BELLATRIX_FORK_EPOCH", bellatrixForkEpoch);
    rawConfig.accept("TERMINAL_TOTAL_DIFFICULTY", terminalTotalDifficulty);
    rawConfig.accept("TERMINAL_BLOCK_HASH", terminalBlockHash);
    rawConfig.accept("TERMINAL_BLOCK_HASH_ACTIVATION_EPOCH", terminalBlockHashActivationEpoch);
  }

  public BellatrixBuilder bellatrixForkVersion(final Bytes4 bellatrixForkVersion) {
    checkNotNull(bellatrixForkVersion);
    this.bellatrixForkVersion = bellatrixForkVersion;
    return this;
  }

  public BellatrixBuilder bellatrixForkEpoch(final UInt64 bellatrixForkEpoch) {
    checkNotNull(bellatrixForkEpoch);
    this.bellatrixForkEpoch = bellatrixForkEpoch;
    return this;
  }

  public BellatrixBuilder inactivityPenaltyQuotientBellatrix(
      final UInt64 inactivityPenaltyQuotientBellatrix) {
    this.inactivityPenaltyQuotientBellatrix = inactivityPenaltyQuotientBellatrix;
    return this;
  }

  public BellatrixBuilder minSlashingPenaltyQuotientBellatrix(
      final Integer minSlashingPenaltyQuotientBellatrix) {
    this.minSlashingPenaltyQuotientBellatrix = minSlashingPenaltyQuotientBellatrix;
    return this;
  }

  public BellatrixBuilder proportionalSlashingMultiplierBellatrix(
      final Integer proportionalSlashingMultiplierBellatrix) {
    this.proportionalSlashingMultiplierBellatrix = proportionalSlashingMultiplierBellatrix;
    return this;
  }

  public BellatrixBuilder maxBytesPerTransaction(final int maxBytesPerTransaction) {
    this.maxBytesPerTransaction = maxBytesPerTransaction;
    return this;
  }

  public BellatrixBuilder maxTransactionsPerPayload(final int maxTransactionsPerPayload) {
    this.maxTransactionsPerPayload = maxTransactionsPerPayload;
    return this;
  }

  public BellatrixBuilder bytesPerLogsBloom(final int bytesPerLogsBloom) {
    this.bytesPerLogsBloom = bytesPerLogsBloom;
    return this;
  }

  public BellatrixBuilder terminalTotalDifficulty(final UInt256 terminalTotalDifficulty) {
    this.terminalTotalDifficulty = terminalTotalDifficulty;
    return this;
  }

  public BellatrixBuilder terminalBlockHash(final Bytes32 terminalBlockHash) {
    this.terminalBlockHash = terminalBlockHash;
    return this;
  }

  public BellatrixBuilder terminalBlockHashActivationEpoch(
      final UInt64 terminalBlockHashActivationEpoch) {
    this.terminalBlockHashActivationEpoch = terminalBlockHashActivationEpoch;
    return this;
  }

  public BellatrixBuilder maxExtraDataBytes(final int maxExtraDataBytes) {
    this.maxExtraDataBytes = maxExtraDataBytes;
    return this;
  }

  public BellatrixBuilder safeSlotsToImportOptimistically(
      final int safeSlotsToImportOptimistically) {
    this.safeSlotsToImportOptimistically = safeSlotsToImportOptimistically;
    return this;
  }
}
