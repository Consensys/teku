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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarFulu;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = {SpecMilestone.FULU})
public class DataColumnSidecarGossipValidatorTest {
  private final Map<Bytes32, BlockImportResult> invalidBlocks = new HashMap<>();
  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);
  private final MiscHelpersFulu miscHelpersFulu = mock(MiscHelpersFulu.class);
  private final MetricsSystem metricsSystemStub = new StubMetricsSystem();
  private final StubTimeProvider stubTimeProvider = StubTimeProvider.withTimeInMillis(0);
  private DataStructureUtil dataStructureUtil;
  private DataColumnSidecarGossipValidator validator;

  private UInt64 parentSlot;
  private BeaconState postState;

  private UInt64 slot;
  private UInt64 index;
  private UInt64 proposerIndex;
  private Bytes32 blockParentRoot;

  private DataColumnSidecar dataColumnSidecar;

  @BeforeEach
  void setup(final SpecContext specContext) {
    this.dataStructureUtil = specContext.getDataStructureUtil();

    this.validator =
        DataColumnSidecarGossipValidator.create(
            specContext.getSpec(),
            invalidBlocks,
            gossipValidationHelper,
            miscHelpersFulu,
            metricsSystemStub,
            stubTimeProvider);

    parentSlot = UInt64.valueOf(1);

    slot = UInt64.valueOf(2);
    index = UInt64.valueOf(1);
    blockParentRoot = dataStructureUtil.randomBytes32();

    final SignedBeaconBlock signedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlock(slot.longValue(), blockParentRoot);
    proposerIndex = signedBeaconBlock.getProposerIndex();

    dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(signedBeaconBlock, index);

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
    when(miscHelpersFulu.verifyDataColumnSidecarKzgProofs(any(DataColumnSidecar.class)))
        .thenReturn(true);
    when(miscHelpersFulu.verifyDataColumnSidecarInclusionProof(any())).thenReturn(true);
    when(miscHelpersFulu.verifyDataColumnSidecar(any())).thenReturn(true);
  }

  @TestTemplate
  void shouldAccept() {
    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
  }

  @TestTemplate
  void shouldIgnoreWhenSlotIsFromFuture() {
    when(gossipValidationHelper.isSlotFromFuture(slot)).thenReturn(true);

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @TestTemplate
  void shouldIgnoreWhenSlotAlreadyFinalized() {
    when(gossipValidationHelper.isSlotFinalized(slot)).thenReturn(true);

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldRejectInvalidDataColumnSidecar() {
    when(miscHelpersFulu.verifyDataColumnSidecar(any())).thenReturn(false);
    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(
            result ->
                result.equals(
                    InternalValidationResult.reject("DataColumnSidecar has invalid structure")));
  }

  @TestTemplate
  void shouldIgnoreWhenParentIsNotAvailable_blockRoot() {
    when(gossipValidationHelper.isBlockAvailable(blockParentRoot)).thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @TestTemplate
  void shouldRejectIfSignatureIsInvalid() {
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), eq(proposerIndex), any(), eq(postState)))
        .thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldIgnoreWhenParentIsNotAvailableSlot() {
    when(gossipValidationHelper.getSlotForBlockRoot(blockParentRoot)).thenReturn(Optional.empty());

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @TestTemplate
  void shouldRejectWhenParentBlockInvalid() {
    invalidBlocks.put(blockParentRoot, BlockImportResult.FAILED_INVALID_ANCESTRY);

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldRejectWhenParentSlotIsGreater() {
    when(gossipValidationHelper.getSlotForBlockRoot(blockParentRoot))
        .thenReturn(Optional.of(parentSlot.plus(1)));

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldRejectIfFinalizedCheckpointIsNotAnAncestorOfDataColumnSidecarsBlock() {
    when(gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
            dataColumnSidecar.getSlot(),
            DataColumnSidecarFulu.required(dataColumnSidecar)
                .getSignedBlockHeader()
                .getMessage()
                .getParentRoot()))
        .thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldRejectWhenInclusionProofFailsValidation() {
    when(miscHelpersFulu.verifyDataColumnSidecarInclusionProof(any())).thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldRejectIfKzgVerificationFailed() {
    when(miscHelpersFulu.verifyDataColumnSidecarKzgProofs(any(DataColumnSidecar.class)))
        .thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldIgnoreWhenIsNotFirstValidSignature() {
    validator
        .getReceivedValidDataColumnSidecarInfoSet()
        .add(
            new DataColumnSidecarGossipValidator.SlotProposerIndexAndColumnIndex(
                slot, proposerIndex, index));

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldIgnoreIfStateIsUnavailable() {
    when(gossipValidationHelper.getParentStateInBlockEpoch(parentSlot, blockParentRoot, slot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldRejectIfProposerIndexIsWrong() {
    when(gossipValidationHelper.isProposerTheExpectedProposer(proposerIndex, slot, postState))
        .thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldTrackValidInfoSet() {
    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldIgnoreImmediatelyWhenDataColumnFromValidInfoSet() {
    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    verify(miscHelpersFulu).verifyDataColumnSidecarInclusionProof(dataColumnSidecar);
    verify(miscHelpersFulu).verifyDataColumnSidecarKzgProofs(dataColumnSidecar);
    verify(gossipValidationHelper).getParentStateInBlockEpoch(any(), any(), any());
    verify(gossipValidationHelper).isProposerTheExpectedProposer(any(), any(), any());
    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);
    clearInvocations(miscHelpersFulu);

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);

    verify(miscHelpersFulu, never()).verifyDataColumnSidecarInclusionProof(dataColumnSidecar);
    verify(miscHelpersFulu, never()).verifyDataColumnSidecarKzgProofs(dataColumnSidecar);
    verify(gossipValidationHelper, never()).getParentStateInBlockEpoch(any(), any(), any());
    verify(gossipValidationHelper, never()).isProposerTheExpectedProposer(any(), any(), any());
    verify(gossipValidationHelper, never())
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
  }

  @TestTemplate
  void shouldNotVerifyKnownValidSignedHeader() {
    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    verify(miscHelpersFulu).verifyDataColumnSidecarInclusionProof(dataColumnSidecar);
    verify(miscHelpersFulu).verifyDataColumnSidecarKzgProofs(dataColumnSidecar);
    verify(gossipValidationHelper).getParentStateInBlockEpoch(any(), any(), any());
    verify(gossipValidationHelper).isProposerTheExpectedProposer(any(), any(), any());
    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);

    // Other DataColumnSidecar from the same block
    final DataColumnSidecar dataColumnSidecar0 =
        dataStructureUtil.randomDataColumnSidecar(
            DataColumnSidecarFulu.required(dataColumnSidecar).getSignedBlockHeader(), UInt64.ZERO);

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar0))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    verify(miscHelpersFulu).verifyDataColumnSidecarInclusionProof(dataColumnSidecar0);
    verify(miscHelpersFulu).verifyDataColumnSidecarKzgProofs(dataColumnSidecar0);
    verify(gossipValidationHelper, never()).getParentStateInBlockEpoch(any(), any(), any());
    verify(gossipValidationHelper, never()).isProposerTheExpectedProposer(any(), any(), any());
    verify(gossipValidationHelper, never())
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);

    // DataColumnSidecar from the new block
    final DataColumnSidecar dataColumnSidecarNew =
        dataStructureUtil.randomDataColumnSidecar(
            dataStructureUtil.randomSignedBeaconBlockHeader(), UInt64.ZERO);
    final Bytes32 parentRoot =
        DataColumnSidecarFulu.required(dataColumnSidecarNew)
            .getSignedBlockHeader()
            .getMessage()
            .getParentRoot();

    when(gossipValidationHelper.isSlotFinalized(dataColumnSidecarNew.getSlot())).thenReturn(false);
    when(gossipValidationHelper.isSlotFromFuture(dataColumnSidecarNew.getSlot())).thenReturn(false);
    when(gossipValidationHelper.isBlockAvailable(parentRoot)).thenReturn(true);
    when(gossipValidationHelper.getSlotForBlockRoot(parentRoot))
        .thenReturn(Optional.of(parentSlot));
    when(gossipValidationHelper.getParentStateInBlockEpoch(
            parentSlot, parentRoot, dataColumnSidecarNew.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
            dataColumnSidecarNew.getSlot(), parentRoot))
        .thenReturn(true);

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecarNew))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);

    verify(miscHelpersFulu).verifyDataColumnSidecarInclusionProof(dataColumnSidecarNew);
    verify(miscHelpersFulu).verifyDataColumnSidecarKzgProofs(dataColumnSidecarNew);
    verify(gossipValidationHelper).getParentStateInBlockEpoch(any(), any(), any());
  }

  @TestTemplate
  void shouldVerifySignedHeaderAgainAfterItDroppedFromCache() {
    final Spec specMock = mock(Spec.class);
    final SpecVersion specVersion = mock(SpecVersion.class);
    when(specMock.getNumberOfDataColumns()).thenReturn(Optional.of(128));
    when(specMock.getGenesisSpec()).thenReturn(specVersion);
    // This will make cache of size 3
    when(specVersion.getSlotsPerEpoch()).thenReturn(1);
    this.validator =
        DataColumnSidecarGossipValidator.create(
            specMock,
            invalidBlocks,
            gossipValidationHelper,
            miscHelpersFulu,
            metricsSystemStub,
            stubTimeProvider);
    // Accept everything
    when(gossipValidationHelper.isSlotFinalized(any())).thenReturn(false);
    when(gossipValidationHelper.isSlotFromFuture(any())).thenReturn(false);
    when(gossipValidationHelper.isBlockAvailable(any())).thenReturn(true);
    when(gossipValidationHelper.getParentStateInBlockEpoch(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(postState)));
    when(gossipValidationHelper.isProposerTheExpectedProposer(any(), any(), any()))
        .thenReturn(true);
    when(gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(any(), any()))
        .thenReturn(true);
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), any(), any(), any()))
        .thenReturn(true);

    // First DataColumnSidecar
    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
    clearInvocations(gossipValidationHelper);

    // Other DataColumnSidecar from the same block, known valid block header is detected, so short
    // validation is used
    final DataColumnSidecar dataColumnSidecar0 =
        dataStructureUtil.randomDataColumnSidecar(
            DataColumnSidecarFulu.required(dataColumnSidecar).getSignedBlockHeader(), UInt64.ZERO);

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar0))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    verify(gossipValidationHelper, never())
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);

    // 2nd block DataColumnSidecar
    final DataColumnSidecar dataColumnSidecar2 = dataStructureUtil.randomDataColumnSidecar();
    when(gossipValidationHelper.getSlotForBlockRoot(any()))
        .thenReturn(Optional.of(dataColumnSidecar2.getSlot().decrement()));

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar2))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);

    // 3rd block DataColumnSidecar
    final DataColumnSidecar dataColumnSidecar3 = dataStructureUtil.randomDataColumnSidecar();
    when(gossipValidationHelper.getSlotForBlockRoot(any()))
        .thenReturn(Optional.of(dataColumnSidecar3.getSlot().decrement()));

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar3))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);

    // 4th block DataColumnSidecar, erasing block from DataColumnSidecar0 from cache
    final DataColumnSidecar dataColumnSidecar4 = dataStructureUtil.randomDataColumnSidecar();
    when(gossipValidationHelper.getSlotForBlockRoot(any()))
        .thenReturn(Optional.of(dataColumnSidecar4.getSlot().decrement()));

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar4))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);

    // DataColumnSidecar from the same block as DataColumnSidecar0 and DataColumnSidecar
    final DataColumnSidecar dataColumnSidecar5 =
        dataStructureUtil.randomDataColumnSidecar(
            DataColumnSidecarFulu.required(dataColumnSidecar).getSignedBlockHeader(),
            UInt64.valueOf(2));
    when(gossipValidationHelper.getSlotForBlockRoot(any()))
        .thenReturn(Optional.of(dataColumnSidecar5.getSlot().decrement()));

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar5))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Signature is validating again though header was known valid until dropped from cache
    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
  }
}
