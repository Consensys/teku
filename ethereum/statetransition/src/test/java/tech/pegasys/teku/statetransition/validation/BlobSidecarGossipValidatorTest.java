/*
 * Copyright Consensys Software Inc., 2023
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = {SpecMilestone.DENEB})
public class BlobSidecarGossipValidatorTest {
  private final Map<Bytes32, BlockImportResult> invalidBlocks = new HashMap<>();
  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);
  private final MiscHelpersDeneb miscHelpersDeneb = mock(MiscHelpersDeneb.class);
  private final KZG kzg = mock(KZG.class);
  private BlobSidecarGossipValidator blobSidecarValidator;

  private UInt64 parentSlot;
  private BeaconState postState;

  private UInt64 slot;
  private UInt64 index;
  private UInt64 proposerIndex;
  private Bytes32 blockRoot;
  private Bytes32 blockParentRoot;

  private BlobSidecar blobSidecar;

  @BeforeEach
  void setup(final SpecContext specContext) {
    final DataStructureUtil dataStructureUtil = specContext.getDataStructureUtil();

    blobSidecarValidator =
        BlobSidecarGossipValidator.create(
            specContext.getSpec(), invalidBlocks, gossipValidationHelper, miscHelpersDeneb, kzg);

    parentSlot = UInt64.valueOf(1);

    slot = UInt64.valueOf(2);
    index = UInt64.valueOf(1);
    proposerIndex = UInt64.valueOf(3);
    blockRoot = dataStructureUtil.randomBytes32();
    blockParentRoot = dataStructureUtil.randomBytes32();

    final BeaconBlockHeader blockHeader =
        new BeaconBlockHeader(
            slot, proposerIndex, blockParentRoot, dataStructureUtil.randomBytes32(), blockRoot);
    blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(
                new SignedBeaconBlockHeader(blockHeader, dataStructureUtil.randomSignature()))
            .index(index)
            .build();

    postState = dataStructureUtil.randomBeaconState();

    // default validate ACCEPT
    when(gossipValidationHelper.isSlotFinalized(slot)).thenReturn(false);
    when(gossipValidationHelper.isSlotFromFuture(slot)).thenReturn(false);
    when(gossipValidationHelper.isBlockAvailable(blockParentRoot)).thenReturn(true);
    when(gossipValidationHelper.getSlotForBlockRoot(blockParentRoot))
        .thenReturn(Optional.of(parentSlot));
    when(gossipValidationHelper.getParentStateInBlockEpoch(parentSlot, blockParentRoot, slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(postState)));
    when(gossipValidationHelper.isProposerTheExpectedProposer(proposerIndex, slot, postState))
        .thenReturn(true);
    when(gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(slot, blockParentRoot))
        .thenReturn(true);
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), eq(proposerIndex), any(), eq(postState)))
        .thenReturn(true);
    when(miscHelpersDeneb.verifyBlobKzgProof(any(), any(BlobSidecar.class))).thenReturn(true);
    when(miscHelpersDeneb.verifyBlobSidecarMerkleProof(any())).thenReturn(true);
  }

  @TestTemplate
  void shouldAccept() {
    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
  }

  @TestTemplate
  void shouldRejectWhenIndexIsTooBig(final SpecContext specContext) {
    final DataStructureUtil dataStructureUtil = specContext.getDataStructureUtil();
    final BeaconBlockHeader blockHeader =
        new BeaconBlockHeader(
            slot, proposerIndex, blockParentRoot, dataStructureUtil.randomBytes32(), blockRoot);
    blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(
                new SignedBeaconBlockHeader(blockHeader, dataStructureUtil.randomSignature()))
            .index(UInt64.valueOf(specContext.getSpec().getMaxBlobsPerBlock().orElseThrow()))
            .build();

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldRejectWhenSlotIsNotDeneb() {
    final Spec mockedSpec = mock(Spec.class);
    when(mockedSpec.getMaxBlobsPerBlock(slot)).thenReturn(Optional.empty());

    blobSidecarValidator =
        BlobSidecarGossipValidator.create(
            mockedSpec, invalidBlocks, gossipValidationHelper, miscHelpersDeneb, kzg);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldIgnoreWhenSlotIsFromFuture() {
    when(gossipValidationHelper.isSlotFromFuture(slot)).thenReturn(true);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @TestTemplate
  void shouldIgnoreWhenSlotAlreadyFinalized() {
    when(gossipValidationHelper.isSlotFinalized(slot)).thenReturn(true);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldIgnoreWhenParentIsNotAvailable_blockRoot() {
    when(gossipValidationHelper.isBlockAvailable(blockParentRoot)).thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @TestTemplate
  void shouldRejectIfSignatureIsInvalid() {
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), eq(proposerIndex), any(), eq(postState)))
        .thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldIgnoreWhenParentIsNotAvailable_slot() {
    when(gossipValidationHelper.getSlotForBlockRoot(blockParentRoot)).thenReturn(Optional.empty());

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @TestTemplate
  void shouldRejectWhenParentBlockInvalid() {
    invalidBlocks.put(blockParentRoot, BlockImportResult.FAILED_INVALID_ANCESTRY);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldRejectWhenParentSlotIsGreater() {
    when(gossipValidationHelper.getSlotForBlockRoot(blockParentRoot))
        .thenReturn(Optional.of(parentSlot.plus(1)));

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldRejectIfFinalizedCheckpointIsNotAnAncestorOfBlobSidecarsBlock() {
    when(gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
            blobSidecar.getSlot(),
            blobSidecar.getSignedBeaconBlockHeader().getMessage().getParentRoot()))
        .thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldRejectWhenInclusionProofFailsValidation(final SpecContext specContext) {
    when(miscHelpersDeneb.verifyBlobSidecarMerkleProof(any())).thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldRejectIfKzgVerificationFailed() {
    when(miscHelpersDeneb.verifyBlobKzgProof(any(), any(BlobSidecar.class))).thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldIgnoreWhenIsNotFirstValidSignature() {
    blobSidecarValidator
        .getReceivedValidBlobSidecarInfoSet()
        .add(
            new BlobSidecarGossipValidator.SlotProposerIndexAndBlobIndex(
                slot, proposerIndex, index));

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldIgnoreIfStateIsUnavailable() {
    when(gossipValidationHelper.getParentStateInBlockEpoch(parentSlot, blockParentRoot, slot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldRejectIfProposerIndexIsWrong() {
    when(gossipValidationHelper.isProposerTheExpectedProposer(proposerIndex, slot, postState))
        .thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldTrackValidInfoSet() {
    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }
}
