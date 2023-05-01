/*
 * Copyright ConsenSys Software Inc., 2023
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
import static tech.pegasys.teku.spec.config.Constants.VALID_BLOCK_SET_SIZE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import java.util.Map;
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
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;

/**
 * This class supposed to implement gossip validation rules as per <a
 * href="https://github.com/ethereum/consensus-specs/blob/dev/specs/deneb/p2p-interface.md#the-gossip-domain-gossipsub">spec</a>
 */
public class BlobSidecarValidator {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final Set<IndexAndBlockRoot> receivedValidBlobSidecarInfoSet;
  private final GossipValidationHelper gossipValidationHelper;
  final Map<Bytes32, BlockImportResult> invalidBlockRoots;

  public static BlobSidecarValidator create(
      final Spec spec,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final GossipValidationHelper validationHelper) {

    final Optional<Integer> maybeMaxBlobsPerBlock = spec.getMaxBlobsPerBlock();

    final int validInfoSize = VALID_BLOCK_SET_SIZE * maybeMaxBlobsPerBlock.orElse(1);

    return new BlobSidecarValidator(
        spec, invalidBlockRoots, validationHelper, LimitedSet.createSynchronized(validInfoSize));
  }

  @VisibleForTesting
  Set<IndexAndBlockRoot> getReceivedValidBlobSidecarInfoSet() {
    return receivedValidBlobSidecarInfoSet;
  }

  private BlobSidecarValidator(
      final Spec spec,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final GossipValidationHelper gossipValidationHelper,
      final Set<IndexAndBlockRoot> receivedValidBlobSidecarInfoSet) {
    this.spec = spec;
    this.invalidBlockRoots = invalidBlockRoots;
    this.gossipValidationHelper = gossipValidationHelper;
    this.receivedValidBlobSidecarInfoSet = receivedValidBlobSidecarInfoSet;
  }

  public SafeFuture<InternalValidationResult> validate(final SignedBlobSidecar signedBlobSidecar) {
    final BlobSidecar blobSidecar = signedBlobSidecar.getBlobSidecar();

    /*
    [REJECT] The sidecar is for the correct topic -- i.e. sidecar.index matches the topic {index}.
    */
    /*
    This rule is already implemented in
    tech.pegasys.teku.networking.eth2.gossip.BlobSidecarGossipManager.TopicIndexAwareOperationProcessor
     */

    /*
    [REJECT] The sidecar's block's parent (defined by sidecar.block_parent_root) passes validation.
     */
    if (invalidBlockRoots.containsKey(blobSidecar.getBlockParentRoot())) {
      return completedFuture(reject("BlobSidecar has an invalid parent block"));
    }

    /*
    [IGNORE] The sidecar is from a slot greater than the latest finalized slot
    -- i.e. validate that sidecar.slot > compute_start_slot_at_epoch(state.finalized_checkpoint.epoch)

    [IGNORE] The sidecar is the only sidecar with valid signature received for the tuple (sidecar.block_root, sidecar.index)
     */
    if (gossipValidationHelper.isSlotFinalized(blobSidecar.getSlot())
        || !isFirstWithValidSignatureForIndexAndBlockRoot(blobSidecar)) {
      LOG.trace(
          "BlobSidecarValidator: BlobSidecar is either too old or is not the first block with valid signature for "
              + "its slot. It will be dropped");
      return completedFuture(InternalValidationResult.IGNORE);
    }

    /*
    [IGNORE] The sidecar is not from a future slot (with a MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance)
    -- i.e. validate that sidecar.slot <= current_slot (a client MAY queue future sidecars for processing
    at the appropriate slot).
     */
    if (gossipValidationHelper.isSlotFromFuture(blobSidecar.getSlot())) {
      LOG.trace(
          "BlobSidecarValidator: BlobSidecar is from the future. It will be saved for future processing");
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }

    /*
    [IGNORE] The sidecar's block's parent (defined by sidecar.block_parent_root) has been seen (via both gossip and
    non-gossip sources) (a client MAY queue sidecars for processing once the parent block is retrieved).
     */
    if (!gossipValidationHelper.isBlockAvailable(blobSidecar.getBlockParentRoot())) {
      LOG.trace(
          "BlobSidecarValidator: BlobSidecar parent is not available. It will be saved for future processing");
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }
    final Optional<UInt64> maybeParentBlockSlot =
        gossipValidationHelper.getSlotForBlockRoot(blobSidecar.getBlockParentRoot());
    if (maybeParentBlockSlot.isEmpty()) {
      LOG.trace(
          "BlobSidecarValidator: Parent block does not exist. It will be saved for future processing");
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }
    final UInt64 parentBlockSlot = maybeParentBlockSlot.get();

    /*
    [REJECT] The sidecar is from a higher slot than the sidecar's block's parent (defined by sidecar.block_parent_root).
     */
    if (parentBlockSlot.isGreaterThanOrEqualTo(blobSidecar.getSlot())) {
      return completedFuture(reject("Parent block is after BlobSidecar slot."));
    }

    return gossipValidationHelper
        .getParentStateInBlockEpoch(
            parentBlockSlot, blobSidecar.getBlockParentRoot(), blobSidecar.getSlot())
        .thenApply(
            maybePostState -> {
              /*
              [REJECT] The sidecar is proposed by the expected proposer_index for the block's slot in the context of the
              current shuffling (defined by block_parent_root/slot). If the proposer_index cannot immediately be verified
              against the expected shuffling, the sidecar MAY be queued for later processing while proposers for the
              block's branch are calculated -- in such a case do not REJECT, instead IGNORE this message.
               */
              if (maybePostState.isEmpty()) {
                LOG.trace(
                    "Block was available but state wasn't. Must have been pruned by finalized.");
                return InternalValidationResult.IGNORE;
              }
              final BeaconState postState = maybePostState.get();
              if (!gossipValidationHelper.isProposerTheExpectedProposer(
                  blobSidecar.getProposerIndex(), blobSidecar.getSlot(), postState)) {
                return reject(
                    "BlobSidecar proposed by incorrect proposer (%s)",
                    blobSidecar.getProposerIndex());
              }

              /*
              [REJECT] The proposer signature, signed_blob_sidecar.signature, is valid with respect to the
              sidecar.proposer_index pubkey.
               */
              if (!isSignatureValidWithRespectToProposerIndex(signedBlobSidecar, postState)) {
                return reject("BlobSidecar signature is invalid");
              }
              if (!receivedValidBlobSidecarInfoSet.add(
                  new IndexAndBlockRoot(
                      signedBlobSidecar.getBlobSidecar().getIndex(),
                      signedBlobSidecar.getBlobSidecar().getBlockRoot()))) {
                return ignore(
                    "Blob is not the first with valid signature for its slot. It will be dropped.");
              }

              return InternalValidationResult.ACCEPT;
            });
  }

  private boolean isSignatureValidWithRespectToProposerIndex(
      SignedBlobSidecar signedBlobSidecar, BeaconState postState) {

    final Bytes32 domain =
        spec.getDomain(
            Domain.DOMAIN_BLOB_SIDECAR,
            spec.getCurrentEpoch(postState),
            postState.getFork(),
            postState.getGenesisValidatorsRoot());
    final Bytes signingRoot = spec.computeSigningRoot(signedBlobSidecar.getBlobSidecar(), domain);

    return gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
        signingRoot,
        signedBlobSidecar.getBlobSidecar().getProposerIndex(),
        signedBlobSidecar.getSignature(),
        postState);
  }

  private boolean isFirstWithValidSignatureForIndexAndBlockRoot(BlobSidecar blobSidecar) {
    return !receivedValidBlobSidecarInfoSet.contains(
        new IndexAndBlockRoot(blobSidecar.getIndex(), blobSidecar.getBlockRoot()));
  }

  static class IndexAndBlockRoot {
    private final UInt64 index;
    private final Bytes32 root;

    IndexAndBlockRoot(final UInt64 index, final Bytes32 root) {
      this.index = index;
      this.root = root;
    }

    public UInt64 getIndex() {
      return index;
    }

    public Bytes32 getRoot() {
      return root;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof IndexAndBlockRoot)) {
        return false;
      }
      IndexAndBlockRoot that = (IndexAndBlockRoot) o;
      return Objects.equal(index, that.index) && Objects.equal(root, that.root);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(index, root);
    }
  }
}
