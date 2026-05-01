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

import java.util.Comparator;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
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

  // bids are valid for the current and next slot, so they're indexed by bid.slot for pruning;
  // the inner set is sorted by value descending for cheap best-bid lookup
  private final ConcurrentNavigableMap<UInt64, NavigableSet<SignedExecutionPayloadBid>> bidsBySlot =
      new ConcurrentSkipListMap<>();

  public DefaultExecutionPayloadBidManager(
      final Spec spec,
      final ExecutionPayloadBidGossipValidator executionPayloadBidGossipValidator,
      final ReceivedExecutionPayloadBidEventsChannel
          receivedExecutionPayloadBidEventsChannelPublisher) {
    this.spec = spec;
    this.executionPayloadBidGossipValidator = executionPayloadBidGossipValidator;
    this.receivedExecutionPayloadBidEventsChannelPublisher =
        receivedExecutionPayloadBidEventsChannelPublisher;
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
            case REJECT, SAVE_FOR_FUTURE, IGNORE -> {}
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
  public SafeFuture<Optional<SignedExecutionPayloadBid>> getBidForBlock(
      final Bytes32 parentRoot,
      final BeaconState state,
      final SafeFuture<GetPayloadResponse> getPayloadResponseFuture,
      final BlockProductionPerformance blockProductionPerformance) {
    final UInt64 slot = state.getSlot();
    final Optional<SignedExecutionPayloadBid> bestRemoteBid = findBestRemoteBid(slot, parentRoot);
    if (bestRemoteBid.isPresent()) {
      final ExecutionPayloadBid bid = bestRemoteBid.get().getMessage();
      LOG.info(
          "Considering remote bid (value: {} ETH, builder index: {}, EL block: {}) for block at slot {}",
          gweiToEth(bid.getValue()),
          bid.getBuilderIndex(),
          formatAbbreviatedHashRoot(bid.getBlockHash()),
          slot);
      blockProductionPerformance.builderBidValidated();
      return SafeFuture.completedFuture(bestRemoteBid);
    }
    return getLocalSelfBuiltBid(parentRoot, slot, getPayloadResponseFuture).thenApply(Optional::of);
  }

  private void addBid(final SignedExecutionPayloadBid signedBid) {
    bidsBySlot
        .computeIfAbsent(
            signedBid.getMessage().getSlot(),
            __ -> new ConcurrentSkipListSet<>(BID_BY_VALUE_DESCENDING))
        .add(signedBid);
  }

  private Optional<SignedExecutionPayloadBid> findBestRemoteBid(
      final UInt64 slot, final Bytes32 parentRoot) {
    final NavigableSet<SignedExecutionPayloadBid> bids = bidsBySlot.get(slot);
    if (bids == null) {
      return Optional.empty();
    }
    // bids iterate from highest to lowest value; return the first one matching parentBlockRoot
    return bids.stream()
        .filter(bid -> bid.getMessage().getParentBlockRoot().equals(parentRoot))
        .findFirst();
  }

  private SafeFuture<SignedExecutionPayloadBid> getLocalSelfBuiltBid(
      final Bytes32 parentRoot,
      final UInt64 slot,
      final SafeFuture<GetPayloadResponse> getPayloadResponseFuture) {
    return getPayloadResponseFuture.thenApply(
        getPayloadResponse -> {
          final SignedExecutionPayloadBid localSelfBuiltSignedBid =
              createLocalSelfBuiltSignedBid(getPayloadResponse, slot, parentRoot);
          LOG.info(
              "Considering self-built bid (value: {} ETH, EL block: {}) for block at slot {}",
              weiToEth(getPayloadResponse.getExecutionPayloadValue()),
              formatAbbreviatedHashRoot(localSelfBuiltSignedBid.getMessage().getBlockHash()),
              slot);
          // no need for gossip validation for local self-built bids
          receivedExecutionPayloadBidEventsChannelPublisher.onExecutionPayloadBidValidated(
              localSelfBuiltSignedBid);
          return localSelfBuiltSignedBid;
        });
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
}
