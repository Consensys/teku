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

import java.util.Collection;
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
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator;
import tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorFormatter;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadBidGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class DefaultExecutionPayloadBidManager
    implements ExecutionPayloadBidManager,
        SlotEventsChannel,
        ReceivedBlockEventsChannel,
        OperationAddedSubscriber<SignedProposerPreferences>,
        ReceivedExecutionPayloadEventsChannel {

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
  private final ExecutionPayloadBidCircuitBreaker executionPayloadBidCircuitBreaker;
  private final ReceivedExecutionPayloadBidEventsChannel
      receivedExecutionPayloadBidEventsChannelPublisher;
  private final PendingPool<SignedExecutionPayloadBid> pendingExecutionPayloadBids;
  private final Subscribers<OperationAddedSubscriber<SignedExecutionPayloadBid>> subscribers =
      Subscribers.create(true);
  private final boolean useShouldOverrideBuilderFlag;

  // bids are valid for the current and next slot, so they're indexed by bid.slot for pruning;
  // the inner set is sorted by value descending for cheap best-bid lookup
  private final ConcurrentNavigableMap<UInt64, NavigableSet<SignedExecutionPayloadBid>> bidsBySlot =
      new ConcurrentSkipListMap<>();

  public DefaultExecutionPayloadBidManager(
      final Spec spec,
      final ExecutionPayloadBidGossipValidator executionPayloadBidGossipValidator,
      final ExecutionPayloadBidCircuitBreaker executionPayloadBidCircuitBreaker,
      final ReceivedExecutionPayloadBidEventsChannel
          receivedExecutionPayloadBidEventsChannelPublisher,
      final PendingPool<SignedExecutionPayloadBid> pendingExecutionPayloadBids,
      final boolean useShouldOverrideBuilderFlag) {
    this.spec = spec;
    this.executionPayloadBidGossipValidator = executionPayloadBidGossipValidator;
    this.executionPayloadBidCircuitBreaker = executionPayloadBidCircuitBreaker;
    this.receivedExecutionPayloadBidEventsChannelPublisher =
        receivedExecutionPayloadBidEventsChannelPublisher;
    this.pendingExecutionPayloadBids = pendingExecutionPayloadBids;
    this.useShouldOverrideBuilderFlag = useShouldOverrideBuilderFlag;
  }

  @Override
  public SafeFuture<InternalValidationResult> validateAndAddBid(
      final SignedExecutionPayloadBid signedBid, final RemoteBidOrigin remoteBidOrigin) {
    return validateAndAddBid(signedBid, remoteBidOrigin == RemoteBidOrigin.P2P);
  }

  @Override
  public void subscribeOperationAdded(
      final OperationAddedSubscriber<SignedExecutionPayloadBid> subscriber) {
    subscribers.subscribe(subscriber);
  }

  private SafeFuture<InternalValidationResult> validateAndAddBid(
      final SignedExecutionPayloadBid signedBid, final boolean fromNetwork) {
    return executionPayloadBidGossipValidator
        .validate(signedBid)
        .thenApply(
            result -> {
              processValidationResult(signedBid, fromNetwork, result);
              return result;
            });
  }

  private void processValidationResult(
      final SignedExecutionPayloadBid signedBid,
      final boolean fromNetwork,
      final InternalValidationResult result) {
    switch (result.code()) {
      case ACCEPT -> {
        addBid(signedBid);
        receivedExecutionPayloadBidEventsChannelPublisher.onExecutionPayloadBidValidated(signedBid);
        subscribers.forEach(
            subscriber -> subscriber.onOperationAdded(signedBid, result, fromNetwork));
      }
      case SAVE_FOR_FUTURE -> pendingExecutionPayloadBids.add(signedBid);
      case REJECT, IGNORE ->
          LOG.debug(
              "Wouldn't consider bid for slot {} from builder {} because it didn't pass gossip validation: {}",
              signedBid.getMessage().getSlot(),
              signedBid.getMessage().getBuilderIndex(),
              result);
    }
  }

  private void retryPendingBids(final Collection<SignedExecutionPayloadBid> pendingBids) {
    // As with non-deferred bids, gossip validation accepts the first valid bid for each
    // (slot, builder index). Reconsider ordering if the spec allows multiple bids per tuple.
    // Deferred gossip was ignored by gossipsub, so accepted retries must be explicitly published.
    pendingBids.forEach(pendingBid -> validateAndAddBid(pendingBid, false).finishError(LOG));
  }

  @Override
  public void onSlot(final UInt64 slot) {
    // bids are valid for the current and next slot, so anything below the current slot is stale
    bidsBySlot.headMap(slot, false).clear();
    pendingExecutionPayloadBids.onSlot(slot);
    // PendingPool prunes historical items only once per epoch, so remove stale bids before retrying
    pendingExecutionPayloadBids.removeItemsMatching(
        pendingBid -> pendingBid.getMessage().getSlot().isLessThan(slot));
    retryPendingBids(pendingExecutionPayloadBids.removeItemsMatching(__ -> true));
  }

  @Override
  public void onOperationAdded(
      final SignedProposerPreferences proposerPreferences,
      final InternalValidationResult validationStatus,
      final boolean fromNetwork) {
    final UInt64 proposalSlot = proposerPreferences.getMessage().getProposalSlot();
    retryPendingBids(
        pendingExecutionPayloadBids.removeItemsMatching(
            pendingBid -> pendingBid.getMessage().getSlot().equals(proposalSlot)));
  }

  @Override
  public void onBlockValidated(final SignedBeaconBlock block) {}

  @Override
  public void onBlockImported(final SignedBeaconBlock block, final boolean executionOptimistic) {
    executionPayloadBidCircuitBreaker.observeImportedBlock(block);
    retryPendingBids(pendingExecutionPayloadBids.removeItemsDependingOn(block.getRoot()));
  }

  @Override
  public void onExecutionPayloadValidated(final SignedExecutionPayloadEnvelope executionPayload) {}

  @Override
  public void onExecutionPayloadImported(
      final SignedExecutionPayloadEnvelope executionPayload, final boolean executionOptimistic) {
    retryPendingBids(
        pendingExecutionPayloadBids.removeItemsDependingOn(executionPayload.getBeaconBlockRoot()));
  }

  @Override
  public SafeFuture<SignedExecutionPayloadBid> getBidForBlock(
      final Bytes32 parentRoot,
      final Bytes32 parentBlockHash,
      final BeaconState state,
      final SafeFuture<GetPayloadResponse> getPayloadResponseFuture,
      final BuilderConfig builderConfig,
      final BlockProductionPerformance blockProductionPerformance) {
    final UInt64 slot = state.getSlot();
    if (executionPayloadBidCircuitBreaker.isEngaged(parentRoot, state)) {
      LOG.info("Builder circuit breaker engaged for Gloas block at slot {}; self-building", slot);
      return getLocalSelfBuiltBid(parentRoot, parentBlockHash, slot, getPayloadResponseFuture);
    }

    final Optional<SignedExecutionPayloadBid> bestRemoteBid =
        findBestRemoteBid(slot, parentRoot, parentBlockHash, state);
    if (bestRemoteBid.isEmpty()) {
      return getLocalSelfBuiltBid(parentRoot, parentBlockHash, slot, getPayloadResponseFuture);
    }
    return selectRemoteOrLocalBid(
        bestRemoteBid.orElseThrow(),
        parentRoot,
        parentBlockHash,
        slot,
        getPayloadResponseFuture,
        builderConfig,
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
      final UInt64 slot,
      final Bytes32 parentRoot,
      final Bytes32 parentBlockHash,
      final BeaconState state) {
    final NavigableSet<SignedExecutionPayloadBid> bids = bidsBySlot.get(slot);
    if (bids == null) {
      return Optional.empty();
    }
    // bids iterate from highest to lowest value; return the first one matching the exact parent
    return bids.stream()
        .filter(bid -> bid.getMessage().getParentBlockRoot().equals(parentRoot))
        .filter(bid -> bid.getMessage().getParentBlockHash().equals(parentBlockHash))
        .filter(
            bid ->
                executionPayloadBidCircuitBreaker.isBuilderAllowed(
                    bid.getMessage().getBuilderIndex(), state))
        .findFirst();
  }

  private SafeFuture<SignedExecutionPayloadBid> getLocalSelfBuiltBid(
      final Bytes32 parentRoot,
      final Bytes32 parentBlockHash,
      final UInt64 slot,
      final SafeFuture<GetPayloadResponse> getPayloadResponseFuture) {
    return getPayloadResponseFuture.thenApply(
        getPayloadResponse ->
            createLocalBid(
                validateLocalResponse(getPayloadResponse, parentBlockHash, slot),
                slot,
                parentRoot));
  }

  /**
   * Selects between the highest eligible remote bid and the locally built execution payload.
   *
   * <p>If the local payload is unavailable or invalid, the remote bid wins. When both are viable,
   * selection follows this precedence:
   *
   * <ol>
   *   <li>If {@code useShouldOverrideBuilderFlag} is enabled and the EL returns {@code
   *       shouldOverrideBuilder}, the local payload wins.
   *   <li>Otherwise, {@code builderConfig.getBuilderBoostFactor()} from the {@link BuilderConfig}
   *       supplied by the validator client is used.
   * </ol>
   *
   * <p>A factor of {@code 0} always prefers a viable local payload, {@code UInt64.MAX_VALUE} always
   * prefers the remote bid, and other factors scale the remote value as a percentage. Equal
   * adjusted values select the local payload.
   */
  private SafeFuture<SignedExecutionPayloadBid> selectRemoteOrLocalBid(
      final SignedExecutionPayloadBid remoteBid,
      final Bytes32 parentRoot,
      final Bytes32 parentBlockHash,
      final UInt64 slot,
      final SafeFuture<GetPayloadResponse> getPayloadResponseFuture,
      final BuilderConfig builderConfig,
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
            return selectLocalBid(localBid, slot);
          }

          final UInt256 remoteValueInWei =
              UInt256.valueOf(remoteBid.getMessage().getValue().bigIntegerValue())
                  .multiply(GWEI_TO_WEI);
          final UInt64 builderBoostFactor = builderConfig.getBuilderBoostFactor();
          final boolean localValueWins =
              BuilderBoostFactorEvaluator.isLocalValueWinning(
                  localResponse.getExecutionPayloadValue(), remoteValueInWei, builderBoostFactor);
          logValueComparison(
              localValueWins,
              builderBoostFactor,
              true,
              localResponse.getExecutionPayloadValue(),
              remoteBid.getMessage(),
              slot);
          return localValueWins
              ? selectLocalBid(localBid, slot)
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

  private SignedExecutionPayloadBid createLocalBid(
      final GetPayloadResponse getPayloadResponse, final UInt64 slot, final Bytes32 parentRoot) {
    return selectLocalBid(createLocalBidCandidate(getPayloadResponse, slot, parentRoot), slot);
  }

  private LocalBidCandidate createLocalBidCandidate(
      final GetPayloadResponse getPayloadResponse, final UInt64 slot, final Bytes32 parentRoot) {
    return new LocalBidCandidate(
        getPayloadResponse, createLocalSelfBuiltSignedBid(getPayloadResponse, slot, parentRoot));
  }

  private SignedExecutionPayloadBid selectLocalBid(
      final LocalBidCandidate localBid, final UInt64 slot) {
    LOG.info(
        "Considering self-built bid (value: {} ETH, EL block: {}) for block at slot {}",
        weiToEth(localBid.response().getExecutionPayloadValue()),
        formatAbbreviatedHashRoot(localBid.signedBid().getMessage().getBlockHash()),
        slot);
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
    final String comparisonFactor =
        BuilderBoostFactorFormatter.formatBuilderBoostFactor(builderBoostFactor);

    LOG.info(
        "{} - builder compare factor: {}, source: {}.",
        localValueWins
            ? String.format(
                "Local execution payload (%s ETH) is chosen over remote bid (%s ETH) for block at slot %s",
                weiToEth(localValue), gweiToEth(remoteBid.getValue()), slot)
            : String.format(
                "Remote bid (%s ETH) is chosen over local execution payload (%s ETH) for block at slot %s",
                gweiToEth(remoteBid.getValue()), weiToEth(localValue), slot),
        comparisonFactor,
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
