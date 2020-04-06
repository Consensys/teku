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

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.core.results.AttestationProcessingResult;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.statetransition.attestation.ForkChoiceAttestationProcessor;
import tech.pegasys.artemis.statetransition.events.attestation.ProcessedAggregateEvent;
import tech.pegasys.artemis.statetransition.events.attestation.ProcessedAttestationEvent;
import tech.pegasys.artemis.statetransition.events.block.ImportedBlockEvent;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.time.channels.SlotEventsChannel;

public class AttestationManager extends Service implements SlotEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final EventBus eventBus;
  private final ForkChoiceAttestationProcessor attestationProcessor;

  private final PendingPool<DelayableAttestation> pendingAttestations;
  private final FutureItems<DelayableAttestation> futureAttestations;

  AttestationManager(
      final EventBus eventBus,
      final ForkChoiceAttestationProcessor attestationProcessor,
      final PendingPool<DelayableAttestation> pendingAttestations,
      final FutureItems<DelayableAttestation> futureAttestations) {
    this.eventBus = eventBus;
    this.attestationProcessor = attestationProcessor;
    this.pendingAttestations = pendingAttestations;
    this.futureAttestations = futureAttestations;
  }

  public static AttestationManager create(
      final EventBus eventBus,
      final PendingPool<DelayableAttestation> pendingAttestations,
      final FutureItems<DelayableAttestation> futureAttestations,
      final ForkChoiceAttestationProcessor forkChoiceAttestationProcessor) {
    return new AttestationManager(
        eventBus, forkChoiceAttestationProcessor, pendingAttestations, futureAttestations);
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onGossipedAttestation(final Attestation attestation) {
    processAttestation(
        new DelayableAttestation(
            attestation, () -> eventBus.post(new ProcessedAttestationEvent(attestation))));
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onAggregateAndProof(final AggregateAndProof aggregateAndProof) {
    final Attestation aggregate = aggregateAndProof.getAggregate();
    processAttestation(
        new DelayableAttestation(
            aggregate, () -> eventBus.post(new ProcessedAggregateEvent(aggregate))));
  }

  @Override
  public void onSlot(final UnsignedLong slot) {
    futureAttestations.prune(slot).forEach(this::processAttestation);
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onBlockImported(final ImportedBlockEvent blockImportedEvent) {
    final SignedBeaconBlock block = blockImportedEvent.getBlock();
    final Bytes32 blockRoot = block.getMessage().hash_tree_root();
    pendingAttestations
        .getItemsDependingOn(blockRoot, false)
        .forEach(
            attestation -> {
              pendingAttestations.remove(attestation);
              processAttestation(attestation);
            });
  }

  private void processAttestation(final DelayableAttestation delayableAttestation) {
    if (pendingAttestations.contains(delayableAttestation)) {
      return;
    }
    final AttestationProcessingResult result =
        attestationProcessor.processAttestation(delayableAttestation.getAttestation());
    if (result.isSuccessful()) {
      LOG.trace("Processed attestation {} successfully", delayableAttestation::hash_tree_root);
      delayableAttestation.onAttestationProcessedSuccessfully();
    } else {
      switch (result.getFailureReason()) {
        case UNKNOWN_BLOCK:
          LOG.trace(
              "Deferring attestation {} as require block is not yet present",
              delayableAttestation::hash_tree_root);
          pendingAttestations.add(delayableAttestation);
          break;
        case ATTESTATION_IS_NOT_FROM_PREVIOUS_SLOT:
        case FOR_FUTURE_EPOCH:
          LOG.trace(
              "Deferring attestation {} until a future slot", delayableAttestation::hash_tree_root);
          futureAttestations.add(delayableAttestation);
          break;
        default:
          LOG.warn("Failed to process attestation: " + result.getFailureMessage());
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
