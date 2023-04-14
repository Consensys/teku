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
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.BlobSidecarValidator.IndexAndBlockRoot;

@TestSpecContext(milestone = {SpecMilestone.DENEB})
public class BlobSidecarValidatorTest {
  private final Map<Bytes32, BlockImportResult> invalidBlocks = new HashMap<>();
  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);
  private BlobSidecarValidator blobSidecarValidator;

  private UInt64 parentSlot;
  private BeaconState postState;

  private UInt64 slot;
  private UInt64 index;
  private UInt64 proposerIndex;
  private Bytes32 blockRoot;
  private Bytes32 blockParentRoot;

  private SignedBlobSidecar signedBlobSidecar;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    final DataStructureUtil dataStructureUtil = specContext.getDataStructureUtil();

    blobSidecarValidator =
        BlobSidecarValidator.create(specContext.getSpec(), invalidBlocks, gossipValidationHelper);

    parentSlot = UInt64.valueOf(1);

    slot = UInt64.valueOf(2);
    index = UInt64.valueOf(1);
    proposerIndex = UInt64.valueOf(3);
    blockRoot = dataStructureUtil.randomBytes32();
    blockParentRoot = dataStructureUtil.randomBytes32();

    signedBlobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .slot(slot)
            .index(index)
            .proposerIndex(proposerIndex)
            .blockRoot(blockRoot)
            .blockParentRoot(blockParentRoot)
            .buildSigned();

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
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), eq(proposerIndex), any(), eq(postState)))
        .thenReturn(true);
  }

  @TestTemplate
  void shouldAccept() {
    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(signedBlobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
  }

  @TestTemplate
  void shouldRejectWhenParentBlockInvalid() {
    invalidBlocks.put(blockParentRoot, BlockImportResult.FAILED_INVALID_ANCESTRY);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(signedBlobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldIgnoreWhenSlotAlreadyFinalized() {
    when(gossipValidationHelper.isSlotFinalized(slot)).thenReturn(true);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(signedBlobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldIgnoreWhenIsNotFirstValidSignature() {
    blobSidecarValidator
        .getReceivedValidBlobSidecarInfoSet()
        .add(new IndexAndBlockRoot(index, blockRoot));

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(signedBlobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldTrackValidInfoSet() {
    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(signedBlobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(signedBlobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldIgnoreWhenSlotIsFromFuture() {
    when(gossipValidationHelper.isSlotFromFuture(slot)).thenReturn(true);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(signedBlobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @TestTemplate
  void shouldIgnoreWhenParentIsNotAvailable_blockRoot() {
    when(gossipValidationHelper.isBlockAvailable(blockParentRoot)).thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(signedBlobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @TestTemplate
  void shouldIgnoreWhenParentIsNotAvailable_slot() {
    when(gossipValidationHelper.getSlotForBlockRoot(blockParentRoot)).thenReturn(Optional.empty());

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(signedBlobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @TestTemplate
  void shouldRejectWhenParentSlotIsGreater() {
    when(gossipValidationHelper.getSlotForBlockRoot(blockParentRoot))
        .thenReturn(Optional.of(parentSlot.plus(1)));

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(signedBlobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldIgnoreIfStateIsUnavailable() {
    when(gossipValidationHelper.getParentStateInBlockEpoch(parentSlot, blockParentRoot, slot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(signedBlobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldRejectIfProposerIndexIsWrong() {
    when(gossipValidationHelper.isProposerTheExpectedProposer(proposerIndex, slot, postState))
        .thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(signedBlobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldRejectIfSignatureIsInvalid() {
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), eq(proposerIndex), any(), eq(postState)))
        .thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(signedBlobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }
}
