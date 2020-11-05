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

package tech.pegasys.teku.statetransition.attestation;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.attestation.ProcessedAttestationListener;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.statetransition.events.block.ImportedBlockEvent;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.AggregateAttestationValidator;
import tech.pegasys.teku.statetransition.validation.AttestationValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public class AttestationManager extends Service implements SlotEventsChannel {

  private static final Logger LOG = LogManager.getLogger();
  private static final SafeFuture<AttestationProcessingResult> ATTESTATION_SAVED_FOR_FUTURE_RESULT =
      SafeFuture.completedFuture(AttestationProcessingResult.SAVED_FOR_FUTURE);

  private final EventBus eventBus;
  private final ForkChoice attestationProcessor;

  private final PendingPool<ValidateableAttestation> pendingAttestations;
  private final FutureItems<ValidateableAttestation> futureAttestations;
  private final AggregatingAttestationPool aggregatingAttestationPool;

  private final Subscribers<ProcessedAttestationListener> attestationsToSendSubscribers =
      Subscribers.create(true);

  private final AttestationValidator attestationValidator;
  private final AggregateAttestationValidator aggregateValidator;

  AttestationManager(
      final EventBus eventBus,
      final ForkChoice attestationProcessor,
      final PendingPool<ValidateableAttestation> pendingAttestations,
      final FutureItems<ValidateableAttestation> futureAttestations,
      final AggregatingAttestationPool aggregatingAttestationPool,
      final AttestationValidator attestationValidator,
      final AggregateAttestationValidator aggregateValidator) {
    this.eventBus = eventBus;
    this.attestationProcessor = attestationProcessor;
    this.pendingAttestations = pendingAttestations;
    this.futureAttestations = futureAttestations;
    this.aggregatingAttestationPool = aggregatingAttestationPool;
    this.attestationValidator = attestationValidator;
    this.aggregateValidator = aggregateValidator;
  }

  public static AttestationManager create(
      final EventBus eventBus,
      final PendingPool<ValidateableAttestation> pendingAttestations,
      final FutureItems<ValidateableAttestation> futureAttestations,
      final ForkChoice attestationProcessor,
      final AggregatingAttestationPool aggregatingAttestationPool,
      final AttestationValidator attestationValidator,
      final AggregateAttestationValidator aggregateValidator) {
    return new AttestationManager(
        eventBus,
        attestationProcessor,
        pendingAttestations,
        futureAttestations,
        aggregatingAttestationPool,
        attestationValidator,
        aggregateValidator);
  }

  public void subscribeToAttestationsToSend(
      ProcessedAttestationListener attestationsToSendListener) {
    attestationsToSendSubscribers.subscribe(attestationsToSendListener);
  }

  public SafeFuture<InternalValidationResult> addAttestation(ValidateableAttestation attestation) {
    SafeFuture<InternalValidationResult> validationResult =
        attestationValidator.validate(attestation);
    processInternallyValidatedAttestation(validationResult, attestation);
    return validationResult;
  }

  public SafeFuture<InternalValidationResult> addAggregate(ValidateableAttestation attestation) {
    SafeFuture<InternalValidationResult> validationResult =
        aggregateValidator.validate(attestation);
    processInternallyValidatedAttestation(validationResult, attestation);
    return validationResult;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void processInternallyValidatedAttestation(
      SafeFuture<InternalValidationResult> validationResult, ValidateableAttestation attestation) {
    validationResult.thenAccept(
        internalValidationResult -> {
          if (internalValidationResult.equals(InternalValidationResult.ACCEPT)
              || internalValidationResult.equals(InternalValidationResult.SAVE_FOR_FUTURE)) {
            onAttestation(attestation)
                .finish(
                    result ->
                        result.ifInvalid(
                            reason -> LOG.debug("Rejected received attestation: " + reason)),
                    err -> LOG.error("Failed to process received attestation.", err));
          }
        });
  }

  @Override
  public void onSlot(final UInt64 slot) {
    futureAttestations.onSlot(slot);
    List<ValidateableAttestation> attestations = futureAttestations.prune(slot);
    if (attestations.isEmpty()) {
      return;
    }
    attestationProcessor.applyIndexedAttestations(attestations);
    attestations.stream()
        .filter(ValidateableAttestation::isProducedLocally)
        .filter(a -> !a.isGossiped())
        .forEach(this::notifyAttestationsToSendSubscribers);
  }

  private void notifyAttestationsToSendSubscribers(ValidateableAttestation attestation) {
    attestationsToSendSubscribers.forEach(s -> s.accept(attestation));
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
              onAttestation(attestation)
                  .finish(
                      err ->
                          LOG.error(
                              "Failed to process pending attestation dependent on " + blockRoot,
                              err));
            });
  }

  public SafeFuture<AttestationProcessingResult> onAttestation(
      final ValidateableAttestation attestation) {
    if (pendingAttestations.contains(attestation)) {
      return ATTESTATION_SAVED_FOR_FUTURE_RESULT;
    }

    return attestationProcessor
        .onAttestation(attestation)
        .thenApply(
            result -> {
              switch (result.getStatus()) {
                case SUCCESSFUL:
                  LOG.trace("Processed attestation {} successfully", attestation::hash_tree_root);
                  aggregatingAttestationPool.add(attestation);
                  sendToSubscribersIfProducedLocally(attestation);
                  break;
                case UNKNOWN_BLOCK:
                  LOG.trace(
                      "Deferring attestation {} as required block is not yet present",
                      attestation::hash_tree_root);
                  pendingAttestations.add(attestation);
                  break;
                case DEFER_FORK_CHOICE_PROCESSING:
                  LOG.trace(
                      "Defer fork choice processing of attestation {}",
                      attestation::hash_tree_root);
                  sendToSubscribersIfProducedLocally(attestation);
                  aggregatingAttestationPool.add(attestation);
                  futureAttestations.add(attestation);
                  break;
                case SAVED_FOR_FUTURE:
                  LOG.trace(
                      "Deferring attestation {} until a future slot", attestation::hash_tree_root);
                  aggregatingAttestationPool.add(attestation);
                  futureAttestations.add(attestation);
                  break;
                case INVALID:
                  break;
                default:
                  throw new UnsupportedOperationException(
                      "AttestationProcessingResult is unrecognizable");
              }
              return result;
            });
  }

  private void sendToSubscribersIfProducedLocally(ValidateableAttestation attestation) {
    if (!attestation.isProducedLocally()) {
      return;
    }

    if (attestation.isAggregate()) {
      aggregateValidator.addSeenAggregate(attestation);
    } else {
      attestationValidator.addSeenAttestation(attestation);
    }

    notifyAttestationsToSendSubscribers(attestation);
    attestation.markGossiped();
  }

  @Override
  protected SafeFuture<?> doStart() {
    eventBus.register(this);
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    eventBus.unregister(this);
    return SafeFuture.COMPLETE;
  }
}
