/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.validator.coordinator;

import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.operations.InclusionList;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class InclusionListsBlockUpdater {

  private static final Logger LOG = LogManager.getLogger();
  private final ForkChoiceNotifier forkChoiceNotifier;
  private final ProposersDataManager proposersDataManager;
  private final CombinedChainDataClient combinedChainDataClient;
  private final ExecutionLayerChannel executionLayerChannel;
  private final Spec spec;

  public InclusionListsBlockUpdater(
      final ForkChoiceNotifier forkChoiceNotifier,
      final ProposersDataManager proposersDataManager,
      final CombinedChainDataClient combinedChainDataClient,
      final ExecutionLayerChannel executionLayerChannel,
      final Spec spec) {
    this.forkChoiceNotifier = forkChoiceNotifier;
    this.proposersDataManager = proposersDataManager;
    this.combinedChainDataClient = combinedChainDataClient;
    this.executionLayerChannel = executionLayerChannel;
    this.spec = spec;
  }

  @SuppressWarnings({"FutureReturnValueIgnored", "UnusedReturnValue"})
  public SafeFuture<Optional<Bytes8>> onUpdateBlockWithInclusionListsDue(final UInt64 slot) {
    return combinedChainDataClient
        .getStateAtSlotExact(slot.increment())
        .thenCompose(
            maybeState -> {
              if (maybeState.isEmpty()) {
                LOG.warn(
                    "Ignoring block update with inclusion lists because state at slot {} is not available",
                    slot);
                return SafeFuture.failedFuture(
                    new IllegalStateException("Head state is not yet available"));
              }
              final BeaconState state = maybeState.get();
              if (proposersDataManager.isProposerForSlot(slot.increment(), state)) {
                return updateBlockWithInclusionLists(slot, state);
              } else {
                LOG.trace(
                    "Not a proposer for slot {}, no block creation to update with inclusion lists",
                    slot.increment());
                return SafeFuture.completedFuture(Optional.empty());
              }
            })
        .exceptionally(
            error -> {
              LOG.error("Unable to get state at slot {}.", slot, error);
              return Optional.empty();
            });
  }

  private SafeFuture<Optional<Bytes8>> updateBlockWithInclusionLists(
      final UInt64 slot, final BeaconState state) {
    LOG.info("Updating block with inclusion lists from slot {}", slot);
    final Optional<List<InclusionList>> maybeInclusionLists =
        combinedChainDataClient.getStore().getInclusionLists(slot);
    if (maybeInclusionLists.isPresent()) {
      final List<InclusionList> inclusionLists = maybeInclusionLists.get();
      final List<Transaction> transactions = getInclusionListTransactions(inclusionLists);
      LOG.trace(
          "Updating block with inclusion lists. Found {} ILs with {} txs from slot {}",
          inclusionLists.size(),
          transactions.size(),
          slot);
      final Bytes32 parentRoot = spec.getBlockRootAtSlot(state, slot);
      final UInt64 proposerSlot = slot.increment();
      return forkChoiceNotifier
          .getPayloadId(parentRoot, proposerSlot)
          .thenCompose(
              maybeExecutionPayloadContext ->
                  updateExecutionPayloadWithInclusionLists(
                      proposerSlot, slot, maybeExecutionPayloadContext, parentRoot, transactions))
          .exceptionally(
              error -> {
                LOG.error(
                    "Unable to get payloadId to update block with inclusion lists (parentRoot: {}, slot {})",
                    parentRoot,
                    proposerSlot,
                    error);
                return Optional.empty();
              });
    } else {
      LOG.info("No inclusion lists found for slot {} to include in the block", slot);
      return SafeFuture.completedFuture(Optional.empty());
    }
  }

  private SafeFuture<Optional<Bytes8>> updateExecutionPayloadWithInclusionLists(
      final UInt64 proposerSlot,
      final UInt64 inclusionListsSlot,
      final Optional<ExecutionPayloadContext> maybeExecutionPayloadContext,
      final Bytes32 parentRoot,
      final List<Transaction> transactions) {
    if (maybeExecutionPayloadContext.isEmpty()) {
      LOG.warn(
          "Unable to update block wth inclusion lists. No execution payload context present for slot {} and parent root {}",
          proposerSlot,
          parentRoot);
      return SafeFuture.completedFuture(Optional.empty());
    } else {
      LOG.trace(
          "Updating block with inclusion lists. PayloadId: {}",
          maybeExecutionPayloadContext.get().getPayloadId());
      return executionLayerChannel
          .engineUpdatePayloadWithInclusionList(
              maybeExecutionPayloadContext.get().getPayloadId(), transactions, inclusionListsSlot)
          .thenApply(
              updatePayloadWithInclusionListResponse -> {
                final Optional<Bytes8> maybePayloadId =
                    updatePayloadWithInclusionListResponse.payloadId();
                LOG.info(
                    "Updated block with Inclusion Lists from slot {}. PayloadId: {}",
                    inclusionListsSlot,
                    maybePayloadId);
                return maybePayloadId;
              })
          .exceptionally(
              error -> {
                LOG.error(
                    "Unable to update block with Inclusion Lists from slot {}",
                    inclusionListsSlot,
                    error);
                return Optional.empty();
              });
    }
  }

  private List<Transaction> getInclusionListTransactions(final List<InclusionList> inclusionLists) {
    return inclusionLists.stream()
        .map(InclusionList::getTransactions)
        .flatMap(List::stream)
        .toList();
  }
}
