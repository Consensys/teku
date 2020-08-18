/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.forkchoice;

import static tech.pegasys.teku.core.ForkChoiceUtil.on_attestation;
import static tech.pegasys.teku.core.ForkChoiceUtil.on_block;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class ForkChoice {

  private static final Predicate<Void> ALWAYS_COMMIT = __ -> true;
  private final ReentrantLock lock = new ReentrantLock();
  private final RecentChainData recentChainData;
  private final StateTransition stateTransition;

  public ForkChoice(final RecentChainData recentChainData, final StateTransition stateTransition) {
    this.recentChainData = recentChainData;
    this.stateTransition = stateTransition;
    recentChainData.subscribeStoreInitialized(this::initializeProtoArrayForkChoice);
  }

  private void initializeProtoArrayForkChoice() {
    processHead();
  }

  private void processHead() {
    processHead(Optional.empty());
  }

  public void processHead(UInt64 nodeSlot) {
    processHead(Optional.of(nodeSlot));
  }

  private void processHead(Optional<UInt64> nodeSlot) {
    withLockAndTransaction(
            ALWAYS_COMMIT,
            transaction -> {
              final Checkpoint finalizedCheckpoint =
                  recentChainData.getStore().getFinalizedCheckpoint();
              final Checkpoint justifiedCheckpoint =
                  recentChainData.getStore().getJustifiedCheckpoint();
              return recentChainData
                  .retrieveCheckpointState(justifiedCheckpoint)
                  .thenAccept(
                      justifiedCheckpointState -> {
                        final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
                        Bytes32 headBlockRoot =
                            forkChoiceStrategy.findHead(
                                transaction,
                                finalizedCheckpoint,
                                justifiedCheckpoint,
                                justifiedCheckpointState.orElseThrow());

                        recentChainData.updateHead(
                            headBlockRoot,
                            nodeSlot.orElse(
                                forkChoiceStrategy
                                    .blockSlot(headBlockRoot)
                                    .orElseThrow(
                                        () ->
                                            new IllegalStateException(
                                                "Unable to retrieve the slot of fork choice head"))));
                      });
            })
        .join();
  }

  public SafeFuture<BlockImportResult> onBlock(
      final SignedBeaconBlock block, Optional<BeaconState> preState) {
    return withLockAndTransaction(
            BlockImportResult::isSuccessful,
            transaction -> {
              final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
              final BlockImportResult blockImportResult =
                  on_block(
                      transaction,
                      block,
                      preState,
                      stateTransition,
                      forkChoiceStrategy,
                      beaconState ->
                          transaction.putStateRoot(
                              beaconState.hash_tree_root(),
                              new SlotAndBlockRoot(
                                  beaconState.getSlot(),
                                  beaconState.getLatest_block_header().hash_tree_root())));
              return SafeFuture.completedFuture(blockImportResult);
            })
        .thenApply(
            result -> {
              if (result.isSuccessful()) {
                result
                    .getBlockProcessingRecord()
                    .ifPresent(
                        record ->
                            getForkChoiceStrategy()
                                .onBlock(block.getMessage(), record.getPostState()));
              }
              return result;
            });
  }

  public SafeFuture<AttestationProcessingResult> onAttestation(
      final ValidateableAttestation attestation) {
    return recentChainData
        .retrieveCheckpointState(attestation.getData().getTarget())
        .thenCompose(
            targetState ->
                withLockAndTransaction(
                    AttestationProcessingResult::isSuccessful,
                    transaction -> {
                      final AttestationProcessingResult result =
                          on_attestation(
                              transaction, attestation, targetState, getForkChoiceStrategy());
                      return SafeFuture.completedFuture(result);
                    }));
  }

  public void save() {
    getForkChoiceStrategy().save();
  }

  public void applyIndexedAttestations(final List<ValidateableAttestation> attestations) {
    withLockAndTransaction(
            ALWAYS_COMMIT,
            transaction -> {
              final ForkChoiceStrategy forkChoiceStrategy = getForkChoiceStrategy();
              attestations.stream()
                  .map(ValidateableAttestation::getIndexedAttestation)
                  .forEach(
                      attestation -> forkChoiceStrategy.onAttestation(transaction, attestation));
              return SafeFuture.COMPLETE;
            })
        .reportExceptions();
  }

  private ForkChoiceStrategy getForkChoiceStrategy() {
    return recentChainData
        .getForkChoiceStrategy()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Attempting to perform fork choice operations before store has been initialized"));
  }

  private <T> SafeFuture<T> withLockAndTransaction(
      final Predicate<T> shouldCommit, final Function<MutableStore, SafeFuture<T>> action) {
    lock.lock();
    return SafeFuture.ofComposed(
            () -> {
              final StoreTransaction transaction = recentChainData.startStoreTransaction();
              return action
                  .apply(transaction)
                  .thenCompose(
                      result -> {
                        if (shouldCommit.test(result)) {
                          return transaction.commit().thenApply(__ -> result);
                        }
                        return SafeFuture.completedFuture(result);
                      });
            })
        .alwaysRun(lock::unlock);
  }
}
