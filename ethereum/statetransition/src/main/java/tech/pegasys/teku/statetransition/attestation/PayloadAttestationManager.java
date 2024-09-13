/*
 * Copyright Consensys Software Inc., 2024
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

import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.PayloadAttestationValidator;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;

public class PayloadAttestationManager implements SlotEventsChannel, ReceivedBlockEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final PayloadAttestationValidator payloadAttestationValidator;
  private final ForkChoice forkChoice;

  private final PayloadAttestationPool payloadAttestationPool;
  private final PendingPool<PayloadAttestationMessage> pendingPayloadAttestations;
  private final FutureItems<PayloadAttestationMessage> futurePayloadAttestations;

  public PayloadAttestationManager(
      final PayloadAttestationValidator payloadAttestationValidator,
      final ForkChoice forkChoice,
      final PayloadAttestationPool payloadAttestationPool,
      final PendingPool<PayloadAttestationMessage> pendingPayloadAttestations,
      final FutureItems<PayloadAttestationMessage> futurePayloadAttestations) {
    this.payloadAttestationValidator = payloadAttestationValidator;
    this.forkChoice = forkChoice;
    this.payloadAttestationPool = payloadAttestationPool;
    this.pendingPayloadAttestations = pendingPayloadAttestations;
    this.futurePayloadAttestations = futurePayloadAttestations;
  }

  public SafeFuture<InternalValidationResult> addPayloadAttestation(
      final PayloadAttestationMessage payloadAttestationMessage,
      final Optional<UInt64> arrivalTimestamp) {
    arrivalTimestamp.ifPresent(
        timestamp ->
            LOG.trace(
                "Processing payload attestation for slot {} at {}",
                payloadAttestationMessage.getData().getSlot(),
                timestamp));
    final SafeFuture<InternalValidationResult> validationResult =
        payloadAttestationValidator.validate(payloadAttestationMessage);
    processInternallyPayloadAttestation(validationResult, payloadAttestationMessage);
    return validationResult;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void processInternallyPayloadAttestation(
      final SafeFuture<InternalValidationResult> validationResult,
      final PayloadAttestationMessage payloadAttestationMessage) {
    validationResult.thenAccept(
        internalValidationResult -> {
          if (internalValidationResult.code().equals(ValidationResultCode.ACCEPT)
              || internalValidationResult.code().equals(ValidationResultCode.SAVE_FOR_FUTURE)) {
            onPayloadAttestationMessage(payloadAttestationMessage)
                .finish(
                    result ->
                        result.ifInvalid(
                            reason ->
                                LOG.debug("Rejected received payload attestation: " + reason)),
                    err -> LOG.error("Failed to process received payload attestation.", err));
          }
        });
  }

  private SafeFuture<AttestationProcessingResult> onPayloadAttestationMessage(
      final PayloadAttestationMessage payloadAttestationMessage) {
    return forkChoice
        .onPayloadAttestationMessage(payloadAttestationMessage)
        .thenApply(
            result -> {
              switch (result.getStatus()) {
                case SUCCESSFUL:
                  LOG.trace(
                      "Processed payload attestation {} successfully",
                      payloadAttestationMessage::hashTreeRoot);
                  payloadAttestationPool.add(payloadAttestationMessage);
                  break;
                case UNKNOWN_BLOCK:
                  LOG.trace(
                      "Deferring payload attestation {} as required block is not yet present",
                      payloadAttestationMessage::hashTreeRoot);
                  pendingPayloadAttestations.add(payloadAttestationMessage);
                  break;
                case SAVED_FOR_FUTURE:
                  LOG.trace(
                      "Deferring payload attestation {} until a future slot",
                      payloadAttestationMessage::hashTreeRoot);
                  payloadAttestationPool.add(payloadAttestationMessage);
                  futurePayloadAttestations.add(payloadAttestationMessage);
                  break;
                case DEFER_FORK_CHOICE_PROCESSING, INVALID:
                  break;
                default:
                  throw new UnsupportedOperationException(
                      "AttestationProcessingResult is unrecognizable");
              }
              return result;
            });
  }

  @Override
  public void onSlot(final UInt64 slot) {
    pendingPayloadAttestations.onSlot(slot);
    applyFutureAttestations(slot);
  }

  private void applyFutureAttestations(final UInt64 slot) {
    futurePayloadAttestations.onSlot(slot);
    final List<PayloadAttestationMessage> payloadAttestations =
        futurePayloadAttestations.prune(slot);
    if (payloadAttestations.isEmpty()) {
      return;
    }
    forkChoice.applyPayloadAttestationMessages(payloadAttestations);
  }

  @Override
  public void onBlockValidated(final SignedBeaconBlock block) {
    // No-op
  }

  @Override
  public void onBlockImported(final SignedBeaconBlock block, final boolean executionOptimistic) {
    final Bytes32 blockRoot = block.getMessage().hashTreeRoot();
    pendingPayloadAttestations
        .getItemsDependingOn(blockRoot, false)
        .forEach(
            payloadAttestationMessage -> {
              pendingPayloadAttestations.remove(payloadAttestationMessage);
              onPayloadAttestationMessage(payloadAttestationMessage)
                  .finish(
                      err ->
                          LOG.error(
                              "Failed to process pending payload attestation dependent on "
                                  + blockRoot,
                              err));
            });
  }
}
