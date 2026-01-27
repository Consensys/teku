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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarFulu;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.SpecLogic;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationResult;
import tech.pegasys.teku.spec.logic.common.util.FuluTrackingKey;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DataColumnSidecarGossipValidatorFuluTest
    extends AbstractDataColumnSidecarGossipValidatorTest {

  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);

  private UInt64 parentSlot;
  private BeaconState postState;
  private UInt64 proposerIndex;
  private Bytes32 blockParentRoot;
  private SignedBeaconBlock signedBeaconBlock;

  @Override
  public Spec createSpec(final Consumer<SpecConfigBuilder> configAdapter) {
    return TestSpecFactory.createMinimalFulu(configAdapter);
  }

  @BeforeEach
  protected void setupForkSpecific() {
    final Spec spec =
        createSpec(builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NO_OP));
    this.dataStructureUtil = new DataStructureUtil(spec);

    this.dataColumnSidecarGossipValidator =
        DataColumnSidecarGossipValidator.create(
            spec, invalidBlocks, gossipValidationHelper, metricsSystemStub, stubTimeProvider);

    parentSlot = UInt64.valueOf(1);
    slot = UInt64.valueOf(2);
    index = UInt64.valueOf(1);
    blockParentRoot = dataStructureUtil.randomBytes32();

    signedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlock(slot.longValue(), blockParentRoot);
    proposerIndex = signedBeaconBlock.getProposerIndex();

    dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(signedBeaconBlock, index);

    postState = dataStructureUtil.randomBeaconState();

    // Default mocks for ACCEPT
    when(gossipValidationHelper.isSlotFromFuture(slot)).thenReturn(false);
    when(gossipValidationHelper.isSlotFinalized(slot)).thenReturn(false);
    // Mock for parent block availability check
    when(gossipValidationHelper.isBlockAvailable(blockParentRoot)).thenReturn(true);
    // Mock for beacon block root availability check (hash of the block header)
    when(gossipValidationHelper.isBlockAvailable(dataColumnSidecar.getBeaconBlockRoot()))
        .thenReturn(true);
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
  }

  @Test
  void shouldAccept() {
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    assertValidationMetrics(Map.of(ValidationResultCode.ACCEPT, 1));
  }

  @Test
  void shouldSaveForFutureWhenSlotIsFromFuture() {
    when(gossipValidationHelper.isSlotFromFuture(slot)).thenReturn(true);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @Test
  void shouldIgnoreWhenSlotAlreadyFinalized() {
    when(gossipValidationHelper.isSlotFinalized(slot)).thenReturn(true);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @Test
  void shouldRejectWhenDataColumnSidecarStructureIsInvalid() {
    // Mock Spec to control structure validation behavior
    final Spec mockSpec = mock(Spec.class);
    final SpecVersion mockSpecVersion = mock(SpecVersion.class);
    final SpecVersion mockGenesisSpec = mock(SpecVersion.class);
    final DataColumnSidecarUtil mockValidationHelper = mock(DataColumnSidecarUtil.class);

    when(mockSpec.atSlot(any(UInt64.class))).thenReturn(mockSpecVersion);
    when(mockSpec.getGenesisSpec()).thenReturn(mockGenesisSpec);
    when(mockGenesisSpec.getSlotsPerEpoch()).thenReturn(32);
    when(mockSpec.getNumberOfDataColumns()).thenReturn(Optional.of(128));
    when(mockSpec.getDataColumnSidecarUtil(any(UInt64.class))).thenReturn(mockValidationHelper);

    // Make structure validation fail
    when(mockValidationHelper.verifyDataColumnSidecarStructure(
            any(SpecLogic.class), any(DataColumnSidecar.class)))
        .thenReturn(false);

    // Create a validator with the mocked spec
    final DataColumnSidecarGossipValidator validatorWithMockedSpec =
        DataColumnSidecarGossipValidator.create(
            mockSpec, invalidBlocks, gossipValidationHelper, metricsSystemStub, stubTimeProvider);

    SafeFutureAssert.assertThatSafeFuture(validatorWithMockedSpec.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldRejectIfSignatureIsInvalid() {
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), eq(proposerIndex), any(), eq(postState)))
        .thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);

    assertValidationMetrics(Map.of(ValidationResultCode.REJECT, 1));
  }

  @Test
  void shouldIgnoreWhenParentIsNotAvailableSlot() {
    when(gossipValidationHelper.getSlotForBlockRoot(blockParentRoot)).thenReturn(Optional.empty());

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);

    assertValidationMetrics(Map.of(ValidationResultCode.REJECT, 1));
  }

  @Test
  void shouldRejectWhenParentBlockInvalid() {
    invalidBlocks.put(blockParentRoot, BlockImportResult.FAILED_INVALID_ANCESTRY);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);

    assertValidationMetrics(Map.of(ValidationResultCode.REJECT, 1));
  }

  @Test
  void shouldRejectWhenParentSlotIsGreater() {
    when(gossipValidationHelper.getSlotForBlockRoot(blockParentRoot))
        .thenReturn(Optional.of(parentSlot.plus(1)));

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);

    assertValidationMetrics(Map.of(ValidationResultCode.REJECT, 1));
  }

  @Test
  void shouldRejectIfFinalizedCheckpointIsNotAnAncestorOfDataColumnSidecarsBlock() {
    when(gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
            dataColumnSidecar.getSlot(),
            DataColumnSidecarFulu.required(dataColumnSidecar)
                .getSignedBlockHeader()
                .getMessage()
                .getParentRoot()))
        .thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);

    assertValidationMetrics(Map.of(ValidationResultCode.REJECT, 1));
  }

  // TODO: Tests for inclusion proof and KZG validation failures require mocking at Spec level
  // Temporarily commented out during MiscHelpers refactoring

  @Test
  void shouldIgnoreWhenIsNotFirstValidSignature() {
    dataColumnSidecarGossipValidator
        .getReceivedValidDataColumnSidecarInfoSet()
        .add(new FuluTrackingKey(slot, proposerIndex, index));

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);

    assertValidationMetrics(Map.of(ValidationResultCode.IGNORE, 1));
  }

  @Test
  void shouldIgnoreIfStateIsUnavailable() {
    when(gossipValidationHelper.getParentStateInBlockEpoch(parentSlot, blockParentRoot, slot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);

    assertValidationMetrics(Map.of(ValidationResultCode.IGNORE, 1));
  }

  @Test
  void shouldRejectIfProposerIndexIsWrong() {
    when(gossipValidationHelper.isProposerTheExpectedProposer(proposerIndex, slot, postState))
        .thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);

    assertValidationMetrics(Map.of(ValidationResultCode.REJECT, 1));
  }

  @Test
  void shouldTrackValidInfoSet() {
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);

    assertValidationMetrics(Map.of(ValidationResultCode.ACCEPT, 1, ValidationResultCode.IGNORE, 1));
  }

  @Test
  void shouldIgnoreImmediatelyWhenDataColumnFromValidInfoSet() {
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Verify state-related validations were called
    verify(gossipValidationHelper).getParentStateInBlockEpoch(any(), any(), any());
    verify(gossipValidationHelper).isProposerTheExpectedProposer(any(), any(), any());
    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);

    // Second attempt should be ignored without state validations
    verify(gossipValidationHelper, never()).getParentStateInBlockEpoch(any(), any(), any());
    verify(gossipValidationHelper, never()).isProposerTheExpectedProposer(any(), any(), any());
    verify(gossipValidationHelper, never())
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());

    assertValidationMetrics(Map.of(ValidationResultCode.ACCEPT, 1, ValidationResultCode.IGNORE, 1));
  }

  @Test
  void shouldNotVerifyKnownValidSignedHeader() {
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Verify full validation was performed
    verify(gossipValidationHelper).getParentStateInBlockEpoch(any(), any(), any());
    verify(gossipValidationHelper).isProposerTheExpectedProposer(any(), any(), any());
    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);

    // Other DataColumnSidecar from the same block (with valid inclusion proof)
    final DataColumnSidecar dataColumnSidecar0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(signedBeaconBlock, UInt64.ZERO);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar0))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Second sidecar from same block should skip state validations (cached header)
    verify(gossipValidationHelper, never()).getParentStateInBlockEpoch(any(), any(), any());
    verify(gossipValidationHelper, never()).isProposerTheExpectedProposer(any(), any(), any());
    verify(gossipValidationHelper, never())
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);

    // DataColumnSidecar from the new block (with valid inclusion proof)
    final SignedBeaconBlock newSignedBlock = dataStructureUtil.randomSignedBeaconBlock();
    final DataColumnSidecar dataColumnSidecarNew =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(newSignedBlock, UInt64.ZERO);
    final Bytes32 parentRoot =
        DataColumnSidecarFulu.required(dataColumnSidecarNew)
            .getSignedBlockHeader()
            .getMessage()
            .getParentRoot();

    when(gossipValidationHelper.isSlotFinalized(dataColumnSidecarNew.getSlot())).thenReturn(false);
    when(gossipValidationHelper.isSlotFromFuture(dataColumnSidecarNew.getSlot())).thenReturn(false);
    when(gossipValidationHelper.isBlockAvailable(dataColumnSidecarNew.getBeaconBlockRoot()))
        .thenReturn(true);
    when(gossipValidationHelper.isBlockAvailable(parentRoot)).thenReturn(true);
    when(gossipValidationHelper.getSlotForBlockRoot(parentRoot))
        .thenReturn(Optional.of(parentSlot));
    when(gossipValidationHelper.getParentStateInBlockEpoch(
            parentSlot, parentRoot, dataColumnSidecarNew.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
            dataColumnSidecarNew.getSlot(), parentRoot))
        .thenReturn(true);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecarNew))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);

    // Verify state validation was attempted
    verify(gossipValidationHelper).getParentStateInBlockEpoch(any(), any(), any());

    assertValidationMetrics(Map.of(ValidationResultCode.ACCEPT, 2, ValidationResultCode.IGNORE, 1));
  }

  @Test
  void shouldVerifySignedHeaderAgainAfterItDroppedFromCache() {
    // Create a local mock for this specific test
    final MiscHelpersFulu miscHelpersMock = mock(MiscHelpersFulu.class);
    when(miscHelpersMock.verifyDataColumnSidecarKzgProofs(any(DataColumnSidecar.class)))
        .thenReturn(true);

    final Spec specMock = mock(Spec.class);
    final SpecVersion specVersion = mock(SpecVersion.class);
    final DataColumnSidecarUtil validationHelper = mock(DataColumnSidecarUtil.class);

    when(specMock.getNumberOfDataColumns()).thenReturn(Optional.of(128));
    when(specMock.getGenesisSpec()).thenReturn(specVersion);
    when(specMock.atSlot(any())).thenReturn(specVersion);
    when(specMock.getDataColumnSidecarUtil(any(UInt64.class))).thenReturn(validationHelper);
    when(specVersion.getDataColumnSidecarUtil()).thenReturn(Optional.of(validationHelper));
    when(specVersion.miscHelpers()).thenReturn(miscHelpersMock);
    when(validationHelper.getBlockHeader(any()))
        .thenAnswer(
            invocation -> {
              DataColumnSidecar sidecar = invocation.getArgument(0);
              return Optional.of(
                  DataColumnSidecarFulu.required(sidecar).getSignedBlockHeader().getMessage());
            });
    when(validationHelper.verifyDataColumnSidecarStructure(any(), any())).thenReturn(true);
    when(validationHelper.verifyDataColumnSidecarKzgProofs(any(), any())).thenReturn(true);
    when(validationHelper.validateExecutionPayloadReference(any(), any(), any(), any()))
        .thenReturn(DataColumnSidecarValidationResult.valid());
    when(validationHelper.validateParentBlock(any(), any(), any(), any()))
        .thenReturn(DataColumnSidecarValidationResult.valid());
    when(validationHelper.extractTrackingKey(any()))
        .thenAnswer(
            invocation -> {
              DataColumnSidecar sidecar = invocation.getArgument(0);
              return new FuluTrackingKey(
                  DataColumnSidecarFulu.required(sidecar)
                      .getSignedBlockHeader()
                      .getMessage()
                      .getSlot(),
                  DataColumnSidecarFulu.required(sidecar)
                      .getSignedBlockHeader()
                      .getMessage()
                      .getProposerIndex(),
                  sidecar.getIndex());
            });
    when(validationHelper.extractTrackingKeyFromHeader(any(), any()))
        .thenAnswer(
            invocation -> {
              BeaconBlockHeader header = invocation.getArgument(0);
              DataColumnSidecar sidecar = invocation.getArgument(1);
              return new FuluTrackingKey(
                  header.getSlot(), header.getProposerIndex(), sidecar.getIndex());
            });
    when(validationHelper.verifyInclusionProof(any(), any(), any())).thenReturn(true);
    when(validationHelper.getSignatureVerificationData(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              DataColumnSidecar sidecar = invocation.getArgument(2);
              return Optional.of(
                  new DataColumnSidecarUtil.SignatureVerificationData(
                      DataColumnSidecarFulu.required(sidecar)
                          .getSignedBlockHeader()
                          .getMessage()
                          .hashTreeRoot(),
                      DataColumnSidecarFulu.required(sidecar)
                          .getSignedBlockHeader()
                          .getMessage()
                          .getProposerIndex(),
                      DataColumnSidecarFulu.required(sidecar)
                          .getSignedBlockHeader()
                          .getSignature()));
            });
    // cacheValidatedInfo is called after successful validation - for Fulu it adds to
    // validSignedBlockHeaders
    doAnswer(
            invocation -> {
              DataColumnSidecar sidecar = invocation.getArgument(0);
              Set<Bytes32> validSignedBlockHeaders = invocation.getArgument(1);
              // For Fulu, cache the signed block header hash
              Bytes32 headerHash =
                  DataColumnSidecarFulu.required(sidecar).getSignedBlockHeader().hashTreeRoot();
              validSignedBlockHeaders.add(headerHash);
              return null;
            })
        .when(validationHelper)
        .cacheValidatedInfo(any(), any(), any());

    // This will make cache of size 3
    when(specVersion.getSlotsPerEpoch()).thenReturn(1);
    this.dataColumnSidecarGossipValidator =
        DataColumnSidecarGossipValidator.create(
            specMock, invalidBlocks, gossipValidationHelper, metricsSystemStub, stubTimeProvider);
    // Accept everything
    when(gossipValidationHelper.isSlotFinalized(any())).thenReturn(false);
    when(gossipValidationHelper.isSlotFromFuture(any())).thenReturn(false);
    when(gossipValidationHelper.isBlockAvailable(any())).thenReturn(true);
    when(gossipValidationHelper.getSlotForBlockRoot(any())).thenReturn(Optional.of(parentSlot));
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
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
    clearInvocations(gossipValidationHelper);

    // Other DataColumnSidecar from the same block, known valid block header is detected, so short
    // validation is used
    final DataColumnSidecar dataColumnSidecar0 =
        dataStructureUtil.randomDataColumnSidecar(
            DataColumnSidecarFulu.required(dataColumnSidecar).getSignedBlockHeader(), UInt64.ZERO);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar0))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    verify(gossipValidationHelper, never())
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);

    // 2nd block DataColumnSidecar
    final DataColumnSidecar dataColumnSidecar2 = dataStructureUtil.randomDataColumnSidecar();
    when(gossipValidationHelper.getSlotForBlockRoot(any()))
        .thenReturn(Optional.of(dataColumnSidecar2.getSlot().decrement()));

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar2))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);

    // 3rd block DataColumnSidecar
    final DataColumnSidecar dataColumnSidecar3 = dataStructureUtil.randomDataColumnSidecar();
    when(gossipValidationHelper.getSlotForBlockRoot(any()))
        .thenReturn(Optional.of(dataColumnSidecar3.getSlot().decrement()));

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar3))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);

    // 4th block DataColumnSidecar, erasing block from DataColumnSidecar0 from cache
    final DataColumnSidecar dataColumnSidecar4 = dataStructureUtil.randomDataColumnSidecar();
    when(gossipValidationHelper.getSlotForBlockRoot(any()))
        .thenReturn(Optional.of(dataColumnSidecar4.getSlot().decrement()));

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar4))
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

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar5))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Signature is validating again though header was known valid until dropped from cache
    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());

    assertValidationMetrics(Map.of(ValidationResultCode.ACCEPT, 6));
  }
}
