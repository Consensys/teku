/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition.execution;

import static tech.pegasys.teku.infrastructure.logging.Converter.gweiToEth;
import static tech.pegasys.teku.infrastructure.logging.Converter.weiToEth;
import static tech.pegasys.teku.infrastructure.logging.LogFormatter.formatAbbreviatedHashRoot;
import static tech.pegasys.teku.spec.constants.EthConstants.GWEI_TO_WEI;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator;
import tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorFormatter;
import tech.pegasys.teku.statetransition.execution.DefaultExecutionPayloadBidManager.LocalBid;

public class ExecutionPayloadBidSelector {

  private static final Logger LOG = LogManager.getLogger();

  private final boolean useShouldOverrideBuilderFlag;
  private final ExecutionPayloadBidCircuitBreaker executionPayloadBidCircuitBreaker;

  // remote bids are sorted by value descending so the highest-value bid is iterated first;
  private static final Comparator<SignedExecutionPayloadBid> BID_BY_VALUE_DESCENDING =
      Comparator.<SignedExecutionPayloadBid, UInt64>comparing(bid -> bid.getMessage().getValue())
          .reversed();

  public ExecutionPayloadBidSelector(
      final boolean useShouldOverrideBuilderFlag,
      final ExecutionPayloadBidCircuitBreaker executionPayloadBidCircuitBreaker) {
    this.useShouldOverrideBuilderFlag = useShouldOverrideBuilderFlag;
    this.executionPayloadBidCircuitBreaker = executionPayloadBidCircuitBreaker;
  }

  /**
   * Selects the highest-value remote bid matching the given parent root and parent block hash,
   * filtered by {@code isBuilderAllowed}. Returns empty if {@code bids} no bid passes all filters.
   *
   * <p>The bids are already sorted
   */
  public Optional<SignedExecutionPayloadBid> selectBestRemoteBid(
      final Set<SignedExecutionPayloadBid> remoteBids,
      final Bytes32 parentRoot,
      final Bytes32 parentBlockHash,
      final BeaconState state) {
    return remoteBids.stream()
        .filter(bid -> bid.getMessage().getParentBlockRoot().equals(parentRoot))
        .filter(bid -> bid.getMessage().getParentBlockHash().equals(parentBlockHash))
        .filter(
            bid ->
                executionPayloadBidCircuitBreaker.isBuilderAllowed(
                    bid.getMessage().getBuilderIndex(), state))
        .sorted(BID_BY_VALUE_DESCENDING)
        .findFirst();
  }

  /**
   * Selects between a remote bid and the locally built execution payload.
   *
   * <p>If {@code maybeRemoteBid} is empty, the local bid is returned unconditionally (the caller
   * already decided to self-build). If {@code maybeLocalBid} is empty (local payload unavailable),
   * the remote bid is returned unconditionally. When both are present, selection follows this
   * precedence:
   *
   * <ol>
   *   <li>If {@code useShouldOverrideBuilderFlag} is enabled and the EL returns {@code
   *       should_override_builder}, the local payload wins.
   *   <li>Otherwise, {@code builderConfig.getBuilderBoostFactor()} is used to scale the remote
   *       value. Equal adjusted values select the local payload.
   * </ol>
   */
  public SignedExecutionPayloadBid selectBestBid(
      final Optional<SignedExecutionPayloadBid> maybeRemoteBid,
      final Optional<LocalBid> maybeLocalBid,
      final BuilderConfig builderConfig,
      final UInt64 slot) {

    if (maybeRemoteBid.isEmpty()) {
      return selectLocalBid(
          maybeLocalBid.orElseThrow(
              () ->
                  new IllegalStateException(
                      String.format(
                          "No remote or local bid is available for block at slot %s.", slot))),
          slot);
    }

    final SignedExecutionPayloadBid remoteBid = maybeRemoteBid.get();

    if (maybeLocalBid.isEmpty()) {
      return selectRemoteBid(remoteBid, slot);
    }

    final LocalBid localBid = maybeLocalBid.get();
    if (useShouldOverrideBuilderFlag && localBid.shouldOverrideBuilder()) {
      LOG.info(
          "Selected self-built bid for block at slot {} because should_override_builder is true",
          slot);
      return selectLocalBid(localBid, slot);
    }

    final UInt256 remoteValueInWei =
        UInt256.valueOf(remoteBid.getMessage().getValue().bigIntegerValue()).multiply(GWEI_TO_WEI);
    final UInt64 builderBoostFactor = builderConfig.getBuilderBoostFactor();
    final boolean localValueWins =
        BuilderBoostFactorEvaluator.isLocalValueWinning(
            localBid.valueInWei(), remoteValueInWei, builderBoostFactor);
    logValueComparison(
        localValueWins, builderBoostFactor, localBid.valueInWei(), remoteBid.getMessage(), slot);
    return localValueWins ? selectLocalBid(localBid, slot) : selectRemoteBid(remoteBid, slot);
  }

  private SignedExecutionPayloadBid selectLocalBid(final LocalBid localBid, final UInt64 slot) {
    LOG.info(
        "Using self-built bid (value: {} ETH, EL block: {}) for block at slot {}",
        weiToEth(localBid.valueInWei()),
        formatAbbreviatedHashRoot(localBid.bid().getMessage().getBlockHash()),
        slot);
    return localBid.bid();
  }

  private SignedExecutionPayloadBid selectRemoteBid(
      final SignedExecutionPayloadBid remoteBid, final UInt64 slot) {
    final ExecutionPayloadBid bid = remoteBid.getMessage();
    LOG.info(
        "Using remote bid (value: {} ETH, builder index: {}, EL block: {}) for block at slot {}",
        gweiToEth(bid.getValue()),
        bid.getBuilderIndex(),
        formatAbbreviatedHashRoot(bid.getBlockHash()),
        slot);
    return remoteBid;
  }

  private void logValueComparison(
      final boolean localValueWins,
      final UInt64 builderBoostFactor,
      final UInt256 localValue,
      final ExecutionPayloadBid remoteBid,
      final UInt64 slot) {
    LOG.info(
        "{} - builder boost factor: {}, source: VC.",
        localValueWins
            ? String.format(
                "Local execution payload (%s ETH) is chosen over remote bid (%s ETH) for block at slot %s",
                weiToEth(localValue), gweiToEth(remoteBid.getValue()), slot)
            : String.format(
                "Remote bid (%s ETH) is chosen over local execution payload (%s ETH) for block at slot %s",
                gweiToEth(remoteBid.getValue()), weiToEth(localValue), slot),
        BuilderBoostFactorFormatter.formatBuilderBoostFactor(builderBoostFactor));
  }
}
