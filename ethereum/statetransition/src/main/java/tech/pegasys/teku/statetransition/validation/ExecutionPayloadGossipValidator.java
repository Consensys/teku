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

package tech.pegasys.teku.statetransition.validation;

import static tech.pegasys.teku.spec.config.Constants.RECENT_SEEN_EXECUTION_PAYLOADS_CACHE_SIZE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;

public class ExecutionPayloadGossipValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final GossipValidationHelper gossipValidationHelper;
  private final SigningRootUtil signingRootUtil;

  private final Set<BlockRootAndBuilderIndex> seenPayloads =
      LimitedSet.createSynchronized(RECENT_SEEN_EXECUTION_PAYLOADS_CACHE_SIZE);

  public ExecutionPayloadGossipValidator(
      final Spec spec, final GossipValidationHelper gossipValidationHelper) {
    this.gossipValidationHelper = gossipValidationHelper;
    signingRootUtil = new SigningRootUtil(spec);
  }

  public SafeFuture<InternalValidationResult> validate(
      final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope) {
    final ExecutionPayloadEnvelope envelope = signedExecutionPayloadEnvelope.getMessage();

    final Optional<InternalValidationResult> preBlockValidationResult =
        performPreBlockValidation(envelope);
    if (preBlockValidationResult.isPresent()) {
      return SafeFuture.completedFuture(preBlockValidationResult.get());
    }

    return performWithBlockValidation(envelope)
        .thenCompose(
            maybeResult ->
                maybeResult
                    .map(SafeFuture::completedFuture)
                    .orElseGet(
                        () ->
                            performWithStateValidation(signedExecutionPayloadEnvelope)
                                .thenPeek(result -> markAsSeen(result, envelope))));
  }

  private SafeFuture<Optional<InternalValidationResult>> performWithBlockValidation(
      final ExecutionPayloadEnvelope envelope) {
    return gossipValidationHelper
        .retrieveBlockByRoot(envelope.getBeaconBlockRoot())
        .thenApply(
            maybeBeaconBlock -> {
              if (maybeBeaconBlock.isEmpty()) {
                LOG.trace("Block with root {} is unavailable", envelope.getBeaconBlockRoot());
                return Optional.of(InternalValidationResult.SAVE_FOR_FUTURE);
              }

              final BeaconBlock beaconBlock = maybeBeaconBlock.get();
              final Optional<ExecutionPayloadBid> maybeExecutionPayloadBid =
                  beaconBlock
                      .getBody()
                      .getOptionalSignedExecutionPayloadBid()
                      .map(SignedExecutionPayloadBid::getMessage);

              if (maybeExecutionPayloadBid.isEmpty()) {
                LOG.trace(
                    "Missing SignedExecutionPayloadBid in block with root {}",
                    beaconBlock.getRoot());
                return Optional.of(
                    reject(
                        "Missing SignedExecutionPayloadBid in block with root %s",
                        beaconBlock.getRoot().toHexString()));
              }

              final ExecutionPayloadBid executionPayloadBid = maybeExecutionPayloadBid.get();
              return validateBuilderIndex(envelope, executionPayloadBid)
                  .or(() -> validatePayloadHash(envelope, executionPayloadBid));
            });
  }

  private Optional<InternalValidationResult> performPreBlockValidation(
      final ExecutionPayloadEnvelope envelope) {
    /*
     * [IGNORE] The node has not seen another valid SignedExecutionPayloadEnvelope for this block root from this builder.
     */
    final BlockRootAndBuilderIndex key =
        new BlockRootAndBuilderIndex(envelope.getBeaconBlockRoot(), envelope.getBuilderIndex());
    if (seenPayloads.contains(key)) {
      LOG.trace(
          "Already seen payload for block root {} from builder {}",
          key.blockRoot,
          key.builderIndex);
      return Optional.of(InternalValidationResult.IGNORE);
    }

    final Optional<UInt64> maybeBeaconBlockSlot =
        gossipValidationHelper.getSlotForBlockRoot(envelope.getBeaconBlockRoot());

    /*
     * [SAVE_FOR_FUTURE] The envelope's block root envelope.block_root has been seen (via gossip or non-gossip sources)
     * (a client MAY queue payload for processing once the block is retrieved)
     */
    if (maybeBeaconBlockSlot.isEmpty()) {
      LOG.trace(
          "Block for Execution Payload Envelope not yet seen (root: {}). Saving for future processing.",
          envelope.getBeaconBlockRoot());
      return Optional.of(InternalValidationResult.SAVE_FOR_FUTURE);
    }

    /*
     * [IGNORE] The envelope is from a slot greater than or equal to the latest finalized slot
     * -- i.e. validate that envelope.slot >= compute_start_slot_at_epoch(store.finalized_checkpoint.epoch)
     */
    if (gossipValidationHelper.isSlotFinalized(envelope.getSlot())) {
      LOG.trace(
          "SignedExecutionPayloadEnvelope slot {} is finalized. Dropping.", envelope.getSlot());
      return Optional.of(InternalValidationResult.IGNORE);
    }

    /*
     * [REJECT] block passes validation
     */
    if (!gossipValidationHelper.isBlockAvailable(envelope.getBeaconBlockRoot())) {
      return Optional.of(
          reject(
              "Execution payload envelope's block with root %s is invalid",
              envelope.getBeaconBlockRoot()));
    }

    /*
     * [REJECT] block.slot equals envelope.slot
     */
    if (!envelope.getSlot().equals(maybeBeaconBlockSlot.get())) {
      LOG.trace(
          "SignedExecutionPayloadEnvelope slot {} does not match block slot {}.",
          envelope.getSlot(),
          maybeBeaconBlockSlot.get());
      return Optional.of(
          reject(
              "SignedExecutionPayloadEnvelope slot %s does not match block slot %s.",
              envelope.getSlot(), maybeBeaconBlockSlot.get()));
    }

    return Optional.empty();
  }

  private Optional<InternalValidationResult> validateBuilderIndex(
      final ExecutionPayloadEnvelope envelope, final ExecutionPayloadBid bid) {
    /*
     * [REJECT] envelope.builder_index == bid.builder_index
     */
    if (!envelope.getBuilderIndex().equals(bid.getBuilderIndex())) {
      LOG.trace(
          "Invalid builder index. Envelope had {} but block's bid had {}",
          envelope.getBuilderIndex(),
          bid.getBuilderIndex());
      return Optional.of(
          reject(
              "Invalid builder index. Execution Payload Envelope had %s but the block Execution Payload Bid had %s",
              envelope.getBuilderIndex(), bid.getBuilderIndex()));
    }
    return Optional.empty();
  }

  private Optional<InternalValidationResult> validatePayloadHash(
      final ExecutionPayloadEnvelope envelope, final ExecutionPayloadBid bid) {
    /*
     * [REJECT] payload.block_hash == bid.block_hash
     */
    final ExecutionPayload payload = envelope.getPayload();
    final Bytes32 payloadBlockHash = payload.getBlockHash();
    final Bytes32 bidBlockHash = bid.getBlockHash();
    if (!payloadBlockHash.equals(bidBlockHash)) {
      LOG.trace(
          "Invalid payload block hash. Envelope had {} but bid had {}",
          payloadBlockHash,
          bidBlockHash);
      return Optional.of(
          InternalValidationResult.reject(
              "Invalid payload block hash. Execution Payload Envelope had %s but ExecutionPayload Bid had %s",
              payloadBlockHash, bidBlockHash));
    }
    return Optional.empty();
  }

  private SafeFuture<InternalValidationResult> performWithStateValidation(
      final SignedExecutionPayloadEnvelope envelope) {
    return gossipValidationHelper
        .getStateAtSlotAndBlockRoot(envelope.getSlotAndBlockRoot())
        .thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                LOG.trace("State for block root {} is unavailable.", envelope.getBeaconBlockRoot());
                return InternalValidationResult.IGNORE;
              }
              /*
               * [REJECT] signed_execution_payload_envelope.signature is valid with respect to the builder's public key
               */
              if (!isSignatureValid(envelope, maybeState.get())) {
                return reject("Invalid SignedExecutionPayloadEnvelope signature");
              }
              return InternalValidationResult.ACCEPT;
            });
  }

  private boolean isSignatureValid(
      final SignedExecutionPayloadEnvelope envelope, final BeaconState postState) {
    final Bytes signingRoot =
        signingRootUtil.signingRootForSignExecutionPayloadEnvelope(
            envelope.getMessage(), postState.getForkInfo());
    return gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
        signingRoot, envelope.getMessage().getBuilderIndex(), envelope.getSignature(), postState);
  }

  private void markAsSeen(
      final InternalValidationResult result, final ExecutionPayloadEnvelope envelope) {
    if (result.isAccept()) {
      seenPayloads.add(
          new BlockRootAndBuilderIndex(envelope.getBeaconBlockRoot(), envelope.getBuilderIndex()));
    }
  }

  private record BlockRootAndBuilderIndex(Bytes32 blockRoot, UInt64 builderIndex) {}
}
