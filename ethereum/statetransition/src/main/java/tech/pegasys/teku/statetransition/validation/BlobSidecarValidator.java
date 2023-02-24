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
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

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
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;

public class BlobSidecarValidator {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final Set<SlotAndBlockRoot> receivedValidBlobSidecarInfoSet;
  private final GossipValidationHelper gossipValidationHelper;
  final Map<Bytes32, BlockImportResult> invalidBlockRoots;

  public static BlobSidecarValidator create(
      final Spec spec,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final GossipValidationHelper validationHelper) {

    final Optional<Integer> maybeMaxBlobsPerBlock =
        spec.forMilestone(spec.getForkSchedule().getHighestSupportedMilestone())
            .getConfig()
            .toVersionDeneb()
            .map(SpecConfigDeneb::getMaxBlobsPerBlock);

    final int validInfoSize = VALID_BLOCK_SET_SIZE * maybeMaxBlobsPerBlock.orElse(1);

    return new BlobSidecarValidator(
        spec, invalidBlockRoots, validationHelper, LimitedSet.createSynchronized(validInfoSize));
  }

  private BlobSidecarValidator(
      final Spec spec,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final GossipValidationHelper gossipValidationHelper,
      final Set<SlotAndBlockRoot> receivedValidBlobSidecarInfoSet) {
    this.spec = spec;
    this.invalidBlockRoots = invalidBlockRoots;
    this.gossipValidationHelper = gossipValidationHelper;
    this.receivedValidBlobSidecarInfoSet = receivedValidBlobSidecarInfoSet;
  }

  public SafeFuture<InternalValidationResult> validate(final SignedBlobSidecar signedBlobSidecar) {

    final BlobSidecar blobSidecar = signedBlobSidecar.getBlobSidecar();

    if (invalidBlockRoots.containsKey(blobSidecar.getBlockParentRoot())) {
      return completedFuture(reject("BlobSidecar has an invalid parent block"));
    }

    if (!gossipValidationHelper.isSlotGreaterThanLatestFinalizedSlot(blobSidecar.getSlot())
        || !blobSidecarIsFirstWithValidSignatureForSlot(blobSidecar)) {
      LOG.trace(
          "BlobSidecarValidator: BlobSidecar is either too old or is not the first block with valid signature for "
              + "its slot. It will be dropped");
      return completedFuture(InternalValidationResult.IGNORE);
    }

    if (gossipValidationHelper.isSlotFromFuture(blobSidecar.getSlot())) {
      LOG.trace(
          "BlobSidecarValidator: BlobSidecar is from the future. It will be saved for future processing");
      return completedFuture(InternalValidationResult.SAVE_FOR_FUTURE);
    }

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

    if (parentBlockSlot.isGreaterThanOrEqualTo(blobSidecar.getSlot())) {
      return completedFuture(reject("Parent block is after BlobSidecar slot."));
    }

    return gossipValidationHelper
        .getParentStateInBlockEpoch(
            parentBlockSlot, blobSidecar.getBlockParentRoot(), blobSidecar.getSlot())
        .thenApply(
            maybePostState -> {
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

              if (!blockSignatureIsValidWithRespectToProposerIndex(signedBlobSidecar, postState)) {
                return reject("BlobSidecar signature is invalid");
              }

              return InternalValidationResult.ACCEPT;
            });
  }

  private boolean blockSignatureIsValidWithRespectToProposerIndex(
      SignedBlobSidecar signedBlobSidecar, BeaconState postState) {

    final Bytes32 domain =
        spec.getDomain(
            Domain.BEACON_PROPOSER,
            spec.getCurrentEpoch(postState),
            postState.getFork(),
            postState.getGenesisValidatorsRoot());
    final Bytes signingRoot = spec.computeSigningRoot(signedBlobSidecar.getBlobSidecar(), domain);

    boolean signatureValid =
        gossipValidationHelper.isSignatureIsValidWithRespectToProposerIndex(
            signingRoot,
            signedBlobSidecar.getBlobSidecar().getProposerIndex(),
            signedBlobSidecar.getSignature(),
            postState);

    return signatureValid
        && receivedValidBlobSidecarInfoSet.add(
            new SlotAndBlockRoot(
                signedBlobSidecar.getBlobSidecar().getSlot(),
                signedBlobSidecar.getBlobSidecar().getBlockRoot()));
  }

  private boolean blobSidecarIsFirstWithValidSignatureForSlot(BlobSidecar blobSidecar) {
    return !receivedValidBlobSidecarInfoSet.contains(
        new SlotAndBlockRoot(blobSidecar.getSlot(), blobSidecar.getBlockRoot()));
  }
}
