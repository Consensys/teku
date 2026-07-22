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
import java.util.NavigableSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadBidGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class DefaultExecutionPayloadBidManager
    implements ExecutionPayloadBidManager, SlotEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  // bids are sorted by value descending so the highest-value bid is iterated first;
  // hashTreeRoot is a tiebreaker to keep the comparator a total order (required by
  // ConcurrentSkipListSet)
  private static final Comparator<SignedExecutionPayloadBid> BID_BY_VALUE_DESCENDING =
      Comparator.<SignedExecutionPayloadBid, UInt64>comparing(bid -> bid.getMessage().getValue())
          .reversed()
          .thenComparing(SszData::hashTreeRoot);

  private final Spec spec;
  private final ExecutionPayloadBidGossipValidator executionPayloadBidGossipValidator;
  private final ReceivedExecutionPayloadBidEventsChannel
      receivedExecutionPayloadBidEventsChannelPublisher;
  private final UInt64 builderBidCompareFactor;
  private final boolean useShouldOverrideBuilderFlag;

  // bids are valid for the current and next slot, so they're indexed by bid.slot for pruning;
  // the inner set is sorted by value descending for cheap best-bid lookup
  private final ConcurrentNavigableMap<UInt64, NavigableSet<SignedExecutionPayloadBid>> bidsBySlot =
      new ConcurrentSkipListMap<>();

  public DefaultExecutionPayloadBidManager(
      final Spec spec,
      final ExecutionPayloadBidGossipValidator executionPayloadBidGossipValidator,
      final ReceivedExecutionPayloadBidEventsChannel
          receivedExecutionPayloadBidEventsChannelPublisher,
      final UInt64 builderBidCompareFactor,
      final boolean useShouldOverrideBuilderFlag) {
    this.spec = spec;
    this.executionPayloadBidGossipValidator = executionPayloadBidGossipValidator;
    this.receivedExecutionPayloadBidEventsChannelPublisher =
        receivedExecutionPayloadBidEventsChannelPublisher;
    this.builderBidCompareFactor = builderBidCompareFactor;
    this.useShouldOverrideBuilderFlag = useShouldOverrideBuilderFlag;
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public SafeFuture<InternalValidationResult> validateAndAddBid(
      final SignedExecutionPayloadBid signedBid, final RemoteBidOrigin remoteBidOrigin) {
    final SafeFuture<InternalValidationResult> validationResult =
        executionPayloadBidGossipValidator.validate(signedBid);
    validationResult.thenAccept(
        result -> {
          switch (result.code()) {
            case ACCEPT -> {
              addBid(signedBid);
              receivedExecutionPayloadBidEventsChannelPublisher.onExecutionPayloadBidValidated(
                  signedBid);
            }
            case SAVE_FOR_FUTURE -> {}
            case REJECT, IGNORE ->
                LOG.debug(
                    "Wouldn't consider a {} bid for slot {} from builder {} because it didn't pass gossip validation: {}",
                    remoteBidOrigin,
                    signedBid.getMessage().getSlot(),
                    signedBid.getMessage().getBuilderIndex(),
                    result);
          }
        });
    return validationResult;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    // bids are valid for the current and next slot, so anything below the current slot is stale
    bidsBySlot.headMap(slot, false).clear();
  }

  @Override
  public SafeFuture<SignedExecutionPayloadBid> getBidForBlock(
      final Bytes32 parentRoot,
      final Bytes32 parentBlockHash,
      final BeaconState state,
      final SafeFuture<GetPayloadResponse> getPayloadResponseFuture,
      final Optional<UInt64> requestedBuilderBoostFactor,
      final BlockProductionPerformance blockProductionPerformance) {
    final UInt64 slot = state.getSlot();
    final Optional<SignedExecutionPayloadBid> bestRemoteBid =
        findBestRemoteBid(slot, parentRoot, parentBlockHash);
    if (bestRemoteBid.isEmpty()) {
      return getLocalSelfBuiltBid(parentRoot, parentBlockHash, slot, getPayloadResponseFuture);
    }
    return selectRemoteOrLocalBid(
        bestRemoteBid.orElseThrow(),
        parentRoot,
        parentBlockHash,
        slot,
        getPayloadResponseFuture,
        requestedBuilderBoostFactor,
        blockProductionPerformance);
  }

  private void addBid(final SignedExecutionPayloadBid signedBid) {
    bidsBySlot
        .computeIfAbsent(
            signedBid.getMessage().getSlot(),
            __ -> new ConcurrentSkipListSet<>(BID_BY_VALUE_DESCENDING))
        .add(signedBid);
  }

  private Optional<SignedExecutionPayloadBid> findBestRemoteBid(
      final UInt64 slot, final Bytes32 parentRoot, final Bytes32 parentBlockHash) {
    final NavigableSet<SignedExecutionPayloadBid> bids = bidsBySlot.get(slot);
    if (bids == null) {
      return Optional.empty();
    }
    // bids iterate from highest to lowest value; return the first one matching the exact parent
    return bids.stream()
        .filter(bid -> bid.getMessage().getParentBlockRoot().equals(parentRoot))
        .filter(bid -> bid.getMessage().getParentBlockHash().equals(parentBlockHash))
        .findFirst();
  }

  private SafeFuture<SignedExecutionPayloadBid> getLocalSelfBuiltBid(
      final Bytes32 parentRoot,
      final Bytes32 parentBlockHash,
      final UInt64 slot,
      final SafeFuture<GetPayloadResponse> getPayloadResponseFuture) {
    return getPayloadResponseFuture.thenApply(
        getPayloadResponse ->
            createAndPublishLocalBid(
                validateLocalResponse(getPayloadResponse, parentBlockHash, slot),
                slot,
                parentRoot));
  }

  private SafeFuture<SignedExecutionPayloadBid> selectRemoteOrLocalBid(
      final SignedExecutionPayloadBid remoteBid,
      final Bytes32 parentRoot,
      final Bytes32 parentBlockHash,
      final UInt64 slot,
      final SafeFuture<GetPayloadResponse> getPayloadResponseFuture,
      final Optional<UInt64> requestedBuilderBoostFactor,
      final BlockProductionPerformance blockProductionPerformance) {
    final SafeFuture<Optional<LocalBidCandidate>> viableLocalBid =
        getPayloadResponseFuture
            .thenApply(
                response ->
                    Optional.of(
                        createLocalBidCandidate(
                            validateLocalResponse(response, parentBlockHash, slot),
                            slot,
                            parentRoot)))
            .exceptionally(
                error -> {
                  LOG.warn(
                      "Local execution payload is unavailable for block at slot {}. Selecting remote bid instead",
                      slot,
                      error);
                  return Optional.empty();
                });

    return viableLocalBid.thenApply(
        maybeLocalBid -> {
          if (maybeLocalBid.isEmpty()) {
            return selectRemoteBid(remoteBid, slot, blockProductionPerformance);
          }

          final LocalBidCandidate localBid = maybeLocalBid.orElseThrow();
          final GetPayloadResponse localResponse = localBid.response();
          if (useShouldOverrideBuilderFlag && localResponse.getShouldOverrideBuilder()) {
            LOG.info(
                "Selected self-built bid for block at slot {} because shouldOverrideBuilder is true",
                slot);
            return publishLocalBid(localBid, slot);
          }

          final UInt256 remoteValueInWei =
              UInt256.valueOf(remoteBid.getMessage().getValue().bigIntegerValue())
                  .multiply(GWEI_TO_WEI);
          final UInt64 builderBoostFactor =
              requestedBuilderBoostFactor.orElse(builderBidCompareFactor);
          final boolean localValueWins =
              BuilderBoostFactorEvaluator.isLocalValueWinning(
                  localResponse.getExecutionPayloadValue(), remoteValueInWei, builderBoostFactor);
          logValueComparison(
              localValueWins,
              builderBoostFactor,
              requestedBuilderBoostFactor.isPresent(),
              localResponse.getExecutionPayloadValue(),
              remoteBid.getMessage(),
              slot);
          return localValueWins
              ? publishLocalBid(localBid, slot)
              : selectRemoteBid(remoteBid, slot, blockProductionPerformance);
        });
  }

  private GetPayloadResponse validateLocalResponse(
      final GetPayloadResponse response, final Bytes32 parentBlockHash, final UInt64 slot) {
    final ExecutionPayload payload = response.getExecutionPayload();
    if (!payload.getParentHash().equals(parentBlockHash)) {
      throw new IllegalStateException(
          String.format(
              "Self-built execution payload parent hash %s does not match selected production parent execution hash %s for block at slot %s",
              formatAbbreviatedHashRoot(payload.getParentHash()),
              formatAbbreviatedHashRoot(parentBlockHash),
              slot));
    }
    if (response.getBlobsBundle().isEmpty()) {
      throw new IllegalStateException("Self-built execution payload is missing blobs bundle");
    }
    if (response.getExecutionRequests().isEmpty()) {
      throw new IllegalStateException("Self-built execution payload is missing execution requests");
    }
    return response;
  }

  private SignedExecutionPayloadBid createAndPublishLocalBid(
      final GetPayloadResponse getPayloadResponse, final UInt64 slot, final Bytes32 parentRoot) {
    return publishLocalBid(createLocalBidCandidate(getPayloadResponse, slot, parentRoot), slot);
  }

  private LocalBidCandidate createLocalBidCandidate(
      final GetPayloadResponse getPayloadResponse, final UInt64 slot, final Bytes32 parentRoot) {
    return new LocalBidCandidate(
        getPayloadResponse, createLocalSelfBuiltSignedBid(getPayloadResponse, slot, parentRoot));
  }

  private SignedExecutionPayloadBid publishLocalBid(
      final LocalBidCandidate localBid, final UInt64 slot) {
    LOG.info(
        "Considering self-built bid (value: {} ETH, EL block: {}) for block at slot {}",
        weiToEth(localBid.response().getExecutionPayloadValue()),
        formatAbbreviatedHashRoot(localBid.signedBid().getMessage().getBlockHash()),
        slot);
    receivedExecutionPayloadBidEventsChannelPublisher.onExecutionPayloadBidValidated(
        localBid.signedBid());
    return localBid.signedBid();
  }

  private SignedExecutionPayloadBid selectRemoteBid(
      final SignedExecutionPayloadBid remoteBid,
      final UInt64 slot,
      final BlockProductionPerformance blockProductionPerformance) {
    final ExecutionPayloadBid bid = remoteBid.getMessage();
    LOG.info(
        "Selected remote bid (value: {} ETH, builder index: {}, EL block: {}) for block at slot {}",
        gweiToEth(bid.getValue()),
        bid.getBuilderIndex(),
        formatAbbreviatedHashRoot(bid.getBlockHash()),
        slot);
    blockProductionPerformance.builderBidValidated();
    return remoteBid;
  }

  private void logValueComparison(
      final boolean localValueWins,
      final UInt64 builderBoostFactor,
      final boolean isRequestedBuilderBoostFactor,
      final UInt256 localValue,
      final ExecutionPayloadBid remoteBid,
      final UInt64 slot) {
    LOG.info(
        "{} - builder compare factor: {}%, source: {}.",
        localValueWins
            ? String.format(
                "Local execution payload (%s ETH) is chosen over remote bid (%s ETH) for block at slot %s",
                weiToEth(localValue), gweiToEth(remoteBid.getValue()), slot)
            : String.format(
                "Remote bid (%s ETH) is chosen over local execution payload (%s ETH) for block at slot %s",
                gweiToEth(remoteBid.getValue()), weiToEth(localValue), slot),
        builderBoostFactor,
        isRequestedBuilderBoostFactor ? "VC" : "BN");
  }

  private SignedExecutionPayloadBid createLocalSelfBuiltSignedBid(
      final GetPayloadResponse getPayloadResponse, final UInt64 slot, final Bytes32 parentRoot) {
    final SpecVersion specVersion = spec.atSlot(slot);
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(specVersion.getSchemaDefinitions());
    final ExecutionPayload executionPayload = getPayloadResponse.getExecutionPayload();
    final SszList<SszKZGCommitment> blobKzgCommitments =
        schemaDefinitions
            .getBlobKzgCommitmentsSchema()
            .createFromBlobsBundle(getPayloadResponse.getBlobsBundle().orElseThrow());
    final Bytes32 executionRequestsRoot =
        getPayloadResponse.getExecutionRequests().orElseThrow().hashTreeRoot();

    final ExecutionPayloadBid bid =
        schemaDefinitions
            .getExecutionPayloadBidSchema()
            .createLocalSelfBuiltBid(
                parentRoot, slot, executionPayload, blobKzgCommitments, executionRequestsRoot);
    // Using G2_POINT_AT_INFINITY as signature for self-builds
    return schemaDefinitions
        .getSignedExecutionPayloadBidSchema()
        .create(bid, BLSSignature.infinity());
  }

  private record LocalBidCandidate(
      GetPayloadResponse response, SignedExecutionPayloadBid signedBid) {}
}
