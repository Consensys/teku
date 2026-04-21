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

package tech.pegasys.teku.spec.logic.versions.gloas.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.datastructures.forkchoice.PayloadStatus.PAYLOAD_STATUS_EMPTY;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.PayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.versions.fulu.util.ForkChoiceUtilFulu;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.statetransition.epoch.EpochProcessorGloas;

public class ForkChoiceUtilGloas extends ForkChoiceUtilFulu {

  public ForkChoiceUtilGloas(
      final SpecConfigGloas specConfig,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final EpochProcessorGloas epochProcessor,
      final AttestationUtilGloas attestationUtil,
      final MiscHelpersGloas miscHelpers) {
    super(specConfig, beaconStateAccessors, epochProcessor, attestationUtil, miscHelpers);
  }

  public static ForkChoiceUtilGloas required(final ForkChoiceUtil forkChoiceUtil) {
    checkArgument(
        forkChoiceUtil instanceof ForkChoiceUtilGloas,
        "Expected a ForkChoiceUtilGloas but was %s",
        forkChoiceUtil.getClass());
    return (ForkChoiceUtilGloas) forkChoiceUtil;
  }

  @Override
  public void applyExecutionPayloadToStore(
      final MutableStore store, final SignedExecutionPayloadEnvelope signedEnvelope) {
    // Add new execution payload to store
    store.putExecutionPayload(signedEnvelope);
  }

  @Override
  public Optional<Integer> getPayloadAttestationDueMillis() {
    final SpecConfigGloas configGloas = SpecConfigGloas.required(specConfig);
    return Optional.of(getSlotComponentDurationMillis(configGloas.getPayloadAttestationDueBps()));
  }

  @Override
  public boolean getFullPayloadVoteHint(final UInt64 attestationIndex) {
    return attestationIndex.equals(UInt64.ONE);
  }

  @Override
  public AvailabilityChecker<?> createAvailabilityCheckerOnBlock(final SignedBeaconBlock block) {
    return AvailabilityChecker.NOOP;
  }

  @Override
  public AvailabilityChecker<?> createAvailabilityCheckerOnExecutionPayloadEnvelope(
      final SignedBeaconBlock block) {
    final AvailabilityCheckerFactory<UInt64> factory =
        this.dataColumnSidecarAvailabilityCheckerFactory;
    if (factory == null) {
      throw new IllegalStateException(
          "DataColumnSidecarAvailabilityCheckerFactory not initialized");
    }
    return factory.createAvailabilityChecker(block);
  }

  @Override
  public int computeCommitteeIndexForAttestation(
      final UInt64 slot,
      final BeaconBlock block,
      final int committeeIndex,
      final ReadOnlyStore store) {
    if (slot.equals(block.getSlot())) {
      return 0;
    }
    return isPayloadVerified(store, block.getRoot()) ? 1 : 0;
  }

  @Override
  public boolean shouldNotifyForkChoiceUpdatedOnBlock() {
    return false;
  }

  /**
   * Return whether the execution payload envelope for the beacon block with root ``root`` has been
   * locally delivered and verified via ``on_execution_payload_envelope``.
   */
  public boolean isPayloadVerified(final ReadOnlyStore store, final Bytes32 root) {
    return store.getExecutionPayloadIfAvailable(root).isPresent();
  }

  // TODO-GLOAS: https://github.com/Consensys/teku/issues/9878
  public boolean shouldExtendPayload(final ReadOnlyStore store, final Bytes32 root) {
    return isPayloadVerified(store, root);
  }

  @Override
  public Optional<ForkChoiceUtilGloas> toVersionGloas() {
    return Optional.of(this);
  }

  /**
   * Determines the payload status of the parent block.
   *
   * <p>Spec reference:
   * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-get_parent_payload_status
   *
   * @param store the fork choice store
   * @param block the current block
   * @return PAYLOAD_STATUS_FULL if parent has full payload, PAYLOAD_STATUS_EMPTY otherwise
   */
  // get_parent_payload_status
  SafeFuture<PayloadStatus> getParentPayloadStatus(
      final ReadOnlyStore store, final BeaconBlock block) {
    return store
        .retrieveBlock(block.getParentRoot())
        .thenApply(
            parentBlock -> {
              if (parentBlock.isEmpty()) {
                throw new IllegalStateException("Parent block not found: " + block.getParentRoot());
              }
              final Optional<Bytes32> maybeMessageBlockHash =
                  parentBlock
                      .get()
                      .getBody()
                      .toVersionGloas()
                      .map(
                          bodyGloas ->
                              bodyGloas.getSignedExecutionPayloadBid().getMessage().getBlockHash());
              // if the parent block is pre-Gloas, we'd use the block state, there would be no
              // payload state
              if (maybeMessageBlockHash.isEmpty()) {
                return PAYLOAD_STATUS_EMPTY;
              }
              final Bytes32 messageBlockHash = maybeMessageBlockHash.get();
              //  Check for uninitialized genesis block hash
              if (messageBlockHash.equals(Bytes32.ZERO)) {
                return PAYLOAD_STATUS_EMPTY;
              }
              final Bytes32 parentBlockHash =
                  BeaconBlockBodyGloas.required(block.getBody())
                      .getSignedExecutionPayloadBid()
                      .getMessage()
                      .getParentBlockHash();
              return parentBlockHash.equals(messageBlockHash)
                  ? PayloadStatus.PAYLOAD_STATUS_FULL
                  : PAYLOAD_STATUS_EMPTY;
            });
  }

  /**
   * Checks if the parent node has a full payload.
   *
   * <p>Spec reference:
   * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#new-is_parent_node_full
   *
   * @param store the fork choice store
   * @param block the current block
   * @return true if parent has full payload status
   */
  // is_parent_node_full
  SafeFuture<Boolean> isParentNodeFull(final ReadOnlyStore store, final BeaconBlock block) {
    return getParentPayloadStatus(store, block)
        .thenApply(parentPayloadStatus -> parentPayloadStatus == PayloadStatus.PAYLOAD_STATUS_FULL);
  }
}
