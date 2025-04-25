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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.operations.InclusionList;
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

  @SuppressWarnings("FutureReturnValueIgnored")
  public void onUpdateBlockWithInclusionListsDue(final UInt64 slot) {
    combinedChainDataClient
        .getBestState()
        .orElseGet(
            () ->
                SafeFuture.failedFuture(
                    new IllegalStateException("Head state is not yet available")))
        .thenAccept(
            state -> {
              if (proposersDataManager.isProposerForSlot(slot.increment(), state)) {
                LOG.info(
                    "onUpdateBlockWithInclusionListsDue, updating block with inclusion lists from slot {}",
                    slot);
                final Optional<List<InclusionList>> maybeInclusionLists =
                    combinedChainDataClient.getStore().getInclusionLists(slot);
                if (maybeInclusionLists.isPresent()) {
                  final List<InclusionList> inclusionLists = maybeInclusionLists.get();
                  final List<Transaction> transactions =
                      getInclusionListTransactions(inclusionLists);
                  LOG.info(
                      "onUpdateBlockWithInclusionListsDue, found {} ILs and {} txs from slot {}",
                      inclusionLists.size(),
                      transactions.size(),
                      slot);
                  final Bytes32 parentRoot = spec.getBlockRootAtSlot(state, slot);
                  LOG.info(
                      "onUpdateBlockWithInclusionListsDue for slot {}, parentRoot {}",
                      slot,
                      parentRoot);
                  forkChoiceNotifier
                      .getPayloadId(parentRoot, slot)
                      .thenAccept(
                          maybeExecutionPayloadContext -> {
                            if (maybeExecutionPayloadContext.isEmpty()) {
                              LOG.warn(
                                  "No execution payload context present for slot {} and parent root {}",
                                  slot,
                                  parentRoot);
                              return;
                            }
                            LOG.info(
                                "onUpdateBlockWithInclusionListsDue, calling EL to update block. PayloadId: {}",
                                maybeExecutionPayloadContext.get().getPayloadId());
                            executionLayerChannel
                                .engineUpdatePayloadWithInclusionList(
                                    maybeExecutionPayloadContext.get().getPayloadId(),
                                    transactions,
                                    slot)
                                .thenAccept(
                                    payloadId ->
                                        LOG.info(
                                            "Updated block with Inclusion Lists from slot {}. PayloadId: {}",
                                            slot,
                                            payloadId.toHexString()));
                          });
                } else {
                  LOG.info("onUpdateBlockWithInclusionListsDue, no inclusion lists empty");
                }
              } else {
                LOG.info(
                    "onUpdateBlockWithInclusionListsDue slot {} not a proposer for {}",
                    slot,
                    slot.increment());
              }
            });
  }

  private List<Transaction> getInclusionListTransactions(final List<InclusionList> inclusionLists) {
    return inclusionLists.stream()
        .map(InclusionList::getTransactions)
        .flatMap(List::stream)
        .toList();
  }
}
