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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.PayloadStatusGloas;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
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
      final MutableStore store,
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final BeaconState postState) {
    // Add new execution payload to store
    store.putExecutionPayloadAndState(signedEnvelope, postState);
  }

  // Checking of blob data availability is delayed until the processing of the execution payload
  @Override
  public AvailabilityChecker<?> createAvailabilityChecker(final SignedBeaconBlock block) {
    return AvailabilityChecker.NOOP_DATACOLUMN_SIDECAR;
  }

  // TODO-GLOAS: https://github.com/Consensys/teku/issues/9878 add a real data availability check
  // (not required for devnet-0)
  @Override
  public AvailabilityChecker<?> createAvailabilityChecker(
      final SignedExecutionPayloadEnvelope executionPayload) {
    return AvailabilityChecker.NOOP_DATACOLUMN_SIDECAR;
  }

  /**
   * Determines the payload status of the parent block.
   *
   * <p>Spec reference:
   * https://github.com/ethereum/consensus-specs/blob/dev/specs/_features/gloas/fork-choice.md#get_parent_payload_status
   *
   * @param store the fork choice store
   * @param block the current block
   * @return PAYLOAD_STATUS_FULL if parent has full payload, PAYLOAD_STATUS_EMPTY otherwise
   */
  // get_parent_payload_status
  public PayloadStatusGloas getParentPayloadStatus(
      final ReadOnlyStore store, final BeaconBlock block) {
    final SignedBeaconBlock parent =
        store
            .getBlockIfAvailable(block.getParentRoot())
            .orElseThrow(
                () ->
                    new IllegalStateException("Parent block not found: " + block.getParentRoot()));

    final BeaconBlockBodyGloas blockBody = BeaconBlockBodyGloas.required(block.getBody());
    final BeaconBlockBodyGloas parentBody =
        BeaconBlockBodyGloas.required(parent.getMessage().getBody());

    final Bytes32 parentBlockHash =
        blockBody.getSignedExecutionPayloadBid().getMessage().getParentBlockHash();
    final Bytes32 messageBlockHash =
        parentBody.getSignedExecutionPayloadBid().getMessage().getBlockHash();

    return parentBlockHash.equals(messageBlockHash)
        ? PayloadStatusGloas.PAYLOAD_STATUS_FULL
        : PayloadStatusGloas.PAYLOAD_STATUS_EMPTY;
  }

  /**
   * Checks if the parent node has a full payload.
   *
   * <p>Spec reference:
   * https://github.com/ethereum/consensus-specs/blob/dev/specs/_features/gloas/fork-choice.md#is_parent_node_full
   *
   * @param store the fork choice store
   * @param block the current block
   * @return true if parent has full payload status
   */
  // is_parent_node_full
  public boolean isParentNodeFull(final ReadOnlyStore store, final BeaconBlock block) {
    return getParentPayloadStatus(store, block) == PayloadStatusGloas.PAYLOAD_STATUS_FULL;
  }
}
