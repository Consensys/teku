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

package tech.pegasys.teku.sync;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.DelayableAttestation;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.ForkChoiceAttestationProcessor;
import tech.pegasys.teku.statetransition.events.attestation.ProcessedAggregateEvent;
import tech.pegasys.teku.statetransition.events.attestation.ProcessedAttestationEvent;
import tech.pegasys.teku.statetransition.events.block.ImportedBlockEvent;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public class AttestationManager extends Service implements SlotEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final EventBus eventBus;
  private final ForkChoiceAttestationProcessor attestationProcessor;

  private final PendingPool<DelayableAttestation> pendingAttestations;
  private final FutureItems<DelayableAttestation> futureAttestations;
  private final AggregatingAttestationPool aggregatingAttestationPool;

  AttestationManager(
          final EventBus eventBus,
          final ForkChoiceAttestationProcessor attestationProcessor,
          final PendingPool<DelayableAttestation> pendingAttestations,
          final FutureItems<DelayableAttestation> futureAttestations,
          final AggregatingAttestationPool aggregatingAttestationPool) {
    this.eventBus = eventBus;
    this.attestationProcessor = attestationProcessor;
    this.pendingAttestations = pendingAttestations;
    this.futureAttestations = futureAttestations;
    this.aggregatingAttestationPool = aggregatingAttestationPool;
  }

  public static AttestationManager create(
      final EventBus eventBus,
      final PendingPool<DelayableAttestation> pendingAttestations,
      final FutureItems<DelayableAttestation> futureAttestations,
      final ForkChoiceAttestationProcessor forkChoiceAttestationProcessor,
      final AggregatingAttestationPool aggregatingAttestationPool) {
    return new AttestationManager(
        eventBus, forkChoiceAttestationProcessor, pendingAttestations, futureAttestations, aggregatingAttestationPool);
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onGossipedAttestation(final Attestation attestation) {
    processAttestation(new DelayableAttestation(attestation, aggregatingAttestationPool::add));
  }

  @Subscribe
  @SuppressWarnings("unused")
  private void onAggregateAndProof(final SignedAggregateAndProof aggregateAndProof) {
    final Attestation aggregateAttestation = aggregateAndProof.getMessage().getAggregate();
    processAttestation(new DelayableAttestation(aggregateAttestation, aggregatingAttestationPool::add));

  }

  @Override
  public void onSlot(final UnsignedLong slot) {
    futureAttestations.prune(slot).forEach(attestationProcessor::applyAttestationToForkChoice);
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

    switch (attestationProcessor.processAttestation(delayableAttestation)) {
      case SUCCESSFUL:
        LOG.trace("Processed attestation {} successfully", delayableAttestation::hash_tree_root);
        delayableAttestation.onAttestationProcessedSuccessfully();
        break;
      case UNKNOWN_BLOCK:
        LOG.trace(
            "Deferring attestation {} as required block is not yet present",
            delayableAttestation::hash_tree_root);
        pendingAttestations.add(delayableAttestation);
        break;
      case SAVED_FOR_FUTURE:
        LOG.trace(
            "Deferring attestation {} until a future slot", delayableAttestation::hash_tree_root);
        futureAttestations.add(delayableAttestation);
        aggregatingAttestationPool.add(delayableAttestation.getAttestation());
        break;
      case INVALID:
        break;
      default:
        throw new UnsupportedOperationException("AttestationProcessingResult is unrecognizable");
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
