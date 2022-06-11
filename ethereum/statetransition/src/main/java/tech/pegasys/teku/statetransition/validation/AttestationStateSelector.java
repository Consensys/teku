/*
 * Copyright ConsenSys Software Inc., 2022
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

import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttestationStateSelector {
  private final Spec spec;
  private final RecentChainData recentChainData;

  public AttestationStateSelector(final Spec spec, final RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  public SafeFuture<Optional<BeaconState>> getStateToValidate(
      final AttestationData attestationData) {
    final Optional<ChainHead> maybeChainHead = recentChainData.getChainHead();
    if (maybeChainHead.isEmpty()) {
      return completedFuture(Optional.empty());
    }
    final ChainHead chainHead = maybeChainHead.get();

    final Bytes32 targetBlockRoot = attestationData.getBeaconBlockRoot();
    final UInt64 attestationSlot = attestationData.getSlot();
    final UInt64 attestationEpoch = attestationData.getTarget().getEpoch();

    // If targetBlockRoot is the current chain head, use the chain head
    if (chainHead.getRoot().equals(targetBlockRoot)) {
      return chainHead
          .getState()
          .thenCompose(state -> resolveStateForAttestation(attestationData, state));
    }

    final UInt64 earliestSlot =
        spec.getEarliestQueryableSlotForBeaconCommitteeInTargetEpoch(attestationEpoch);
    // If the attestation is within the lookahead period for the finalized state, use that
    // If the target block doesn't descend from finalized the attestation is invalid
    final BeaconState finalizedState = recentChainData.getStore().getLatestFinalized().getState();
    if (finalizedState.getSlot().isGreaterThanOrEqualTo(earliestSlot)) {
      return completedFuture(Optional.of(finalizedState));
    }

    // If it's the same epoch as the current head and is a descendant, use the chain head
    if (attestationEpoch.equals(spec.computeEpochAtSlot(chainHead.getSlot()))) {
      if (isAncestorOfChainHead(chainHead, targetBlockRoot, attestationSlot)) {
        return chainHead.getState().thenApply(Optional::of);
      }
    }

    // Otherwise, use the state from the earliest allowed slot.
    // This maximises the chance that the state we get will be on the canonical fork and so useful
    // for other requests, and means all attestations for that epoch refer to the same slot,
    // minimising the number of states we need
    final Optional<UInt64> targetBlockSlot = recentChainData.getSlotForBlockRoot(targetBlockRoot);
    if (targetBlockSlot.isEmpty()) {
      // Block became unknown, so ignore it
      return completedFuture(Optional.empty());
    }
    final Checkpoint requiredCheckpoint;
    if (targetBlockSlot.get().isLessThan(earliestSlot)) {
      // Target block is from before the earliest slot so just roll it forward.
      requiredCheckpoint = new Checkpoint(spec.computeEpochAtSlot(earliestSlot), targetBlockRoot);
    } else {
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
          recentChainData.getForkChoiceStrategy().orElseThrow();
      final Optional<Bytes32> maybeAncestorRoot =
          forkChoiceStrategy.getAncestor(targetBlockRoot, earliestSlot);
      if (maybeAncestorRoot.isEmpty()) {
        // The target block has become unknown or doesn't extend from finalized anymore
        // so we can now ignore it.
        return completedFuture(Optional.empty());
      }
      requiredCheckpoint =
          new Checkpoint(spec.computeEpochAtSlot(earliestSlot), maybeAncestorRoot.get());
    }
    return recentChainData.retrieveCheckpointState(requiredCheckpoint);
  }

  private Boolean isAncestorOfChainHead(
      final ChainHead chainHead, final Bytes32 targetBlockRoot, final UInt64 attestationSlot) {
    return recentChainData
        .getForkChoiceStrategy()
        .orElseThrow()
        .getAncestor(chainHead.getRoot(), attestationSlot)
        .map(canonicalRoot -> canonicalRoot.equals(targetBlockRoot))
        .orElse(false);
  }

  /**
   * Committee information is only guaranteed to be stable up to 1 epoch ahead, if block attested to
   * is too old, we need to roll the corresponding state forward to process the attestation
   *
   * @param attestationData The attestation data to be processed
   * @param blockState The state corresponding to the block being attested to
   * @return The state to use for validation of this attestation
   */
  private SafeFuture<Optional<BeaconState>> resolveStateForAttestation(
      final AttestationData attestationData, final BeaconState blockState) {
    final Bytes32 blockRoot = attestationData.getBeaconBlockRoot();
    final Checkpoint targetEpoch = attestationData.getTarget();
    final UInt64 earliestSlot =
        spec.getEarliestQueryableSlotForBeaconCommitteeInTargetEpoch(targetEpoch.getEpoch());
    final UInt64 earliestEpoch = spec.computeEpochAtSlot(earliestSlot);
    if (blockState.getSlot().isLessThan(earliestSlot)) {
      final Checkpoint checkpoint = new Checkpoint(earliestEpoch, blockRoot);
      return recentChainData.getStore().retrieveCheckpointState(checkpoint, blockState);
    } else {
      return completedFuture(Optional.of(blockState));
    }
  }
}
