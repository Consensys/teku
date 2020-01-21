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

package tech.pegasys.artemis.sync;

import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.on_attestation;
import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.attestation.AttestationProcessingResult;
import tech.pegasys.artemis.statetransition.events.BlockImportedEvent;
import tech.pegasys.artemis.statetransition.events.ProcessedAggregateEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.util.async.SafeFuture;

public class AttestationPropagationManager extends Service {
  private static final Logger LOG = LogManager.getLogger();
  private final EventBus eventBus;
  private final ChainStorageClient storageClient;
  private final StateTransition stateTransition;
  private final PendingPool<Attestation> pendingAttestations;
  private final FutureItems<Attestation> futureAttestations;

  private AttestationPropagationManager(
      final EventBus eventBus,
      final ChainStorageClient storageClient,
      final StateTransition stateTransition,
      final PendingPool<Attestation> pendingAttestations,
      final FutureItems<Attestation> futureAttestations) {
    this.eventBus = eventBus;
    this.storageClient = storageClient;
    this.stateTransition = stateTransition;
    this.pendingAttestations = pendingAttestations;
    this.futureAttestations = futureAttestations;
  }

  public static AttestationPropagationManager create(
      final EventBus eventBus, final ChainStorageClient storageClient) {
    final PendingPool<Attestation> pendingAttestations =
        PendingPool.createForAttestations(eventBus);
    final FutureItems<Attestation> futureAttestations =
        new FutureItems<>(attestation -> attestation.getData().getSlot().plus(UnsignedLong.ONE));
    return new AttestationPropagationManager(
        eventBus,
        storageClient,
        new StateTransition(false),
        pendingAttestations,
        futureAttestations);
  }

  @Subscribe
  void onGossipedAttestation(final Attestation attestation) {
    processAttestation(attestation);
    // TODO: Should we post this if the attestation was invalid?
    // What if it was delayed?
    this.eventBus.post(new ProcessedAggregateEvent(attestation));
  }

  @Subscribe
  void onAggregateAndProof(final AggregateAndProof aggregateAndProof) {
    final Attestation aggregate = aggregateAndProof.getAggregate();
    processAttestation(aggregate);
    // TODO: Should we post this if the attestation was invalid?
    // What if it was delayed?
    this.eventBus.post(new ProcessedAggregateEvent(aggregate));
  }

  @Subscribe
  void onSlot(final SlotEvent slotEvent) {
    futureAttestations.prune(slotEvent.getSlot()).forEach(this::processAttestation);
  }

  @Subscribe
  void onBlockImported(final BlockImportedEvent blockImportedEvent) {
    final SignedBeaconBlock block = blockImportedEvent.getBlock();
    final Bytes32 blockRoot = block.getMessage().hash_tree_root();
    pendingAttestations
        .childrenOf(blockRoot)
        .forEach(
            attestation -> {
              pendingAttestations.remove(attestation);
              processAttestation(attestation);
            });
  }

  private void processAttestation(final Attestation attestation) {
    if (pendingAttestations.contains(attestation)) {
      return;
    }
    final Store.Transaction transaction = storageClient.startStoreTransaction();
    final AttestationProcessingResult result =
        on_attestation(transaction, attestation, stateTransition);
    if (result.isSuccessful()) {
      LOG.trace("Processed attestation {} successfully", attestation::hash_tree_root);
      transaction.commit(() -> {}, "Failed to persist attestation result");
    } else {
      switch (result.getFailureReason()) {
        case UNKNOWN_PARENT:
          LOG.trace(
              "Deferring attestation {} as require block is not yet present",
              attestation::hash_tree_root);
          pendingAttestations.add(attestation);
          break;
        case ATTESTATION_IS_NOT_FROM_PREVIOUS_SLOT:
        case FOR_FUTURE_EPOCH:
          LOG.trace("Deferring attestation {} until a future slot", attestation::hash_tree_root);
          futureAttestations.add(attestation);
          break;
        default:
          STDOUT.log(Level.WARN, "Failed to process attestation: " + result.getFailureMessage());
          break;
      }
    }
  }

  @Override
  protected SafeFuture<?> doStart() {
    eventBus.register(this);
    return this.pendingAttestations.start();
  }

  @Override
  protected SafeFuture<?> doStop() {
    eventBus.unregister(this);
    return pendingAttestations.stop();
  }
}
