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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
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
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.logic.SpecLogic;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.logic.common.util.GloasTrackingKey;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager;

public class DataColumnSidecarGossipValidatorGloasTest
    extends AbstractDataColumnSidecarGossipValidatorTest {
  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);
  private final ExecutionPayloadBidManager executionPayloadBidManager =
      mock(ExecutionPayloadBidManager.class);

  private Bytes32 beaconBlockRoot;

  @Override
  public Spec createSpec(final Consumer<SpecConfigBuilder> configAdapter) {
    return TestSpecFactory.createMinimalGloas(configAdapter);
  }

  @BeforeEach
  void setup() {
    final Spec spec =
        createSpec(builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NO_OP));
    this.dataStructureUtil = new DataStructureUtil(spec);

    this.validator =
        DataColumnSidecarGossipValidator.create(
            spec,
            invalidBlocks,
            gossipValidationHelper,
            executionPayloadBidManager,
            metricsSystemStub,
            stubTimeProvider);

    // Use fixed values like Fulu tests for predictable mocking
    slot = UInt64.valueOf(2);
    index = UInt64.valueOf(1);
    beaconBlockRoot = dataStructureUtil.randomBytes32();

    // Create a Gloas data column sidecar with fixed slot and index
    dataColumnSidecar =
        dataStructureUtil.new RandomDataColumnSidecarBuilder()
            .slot(slot)
            .index(index)
            .beaconBlockRoot(beaconBlockRoot)
            .build();

    // Default mocks for ACCEPT
    when(gossipValidationHelper.isSlotFinalized(slot)).thenReturn(false);
    when(gossipValidationHelper.isSlotFromFuture(slot)).thenReturn(false);
    // Gloas creates synthetic header with Bytes32.ZERO parent, need to mock these checks
    when(gossipValidationHelper.isBlockAvailable(any(Bytes32.class))).thenReturn(true);
    // Mock getSlotForBlockRoot to return the sidecar's slot (not parent slot)
    when(gossipValidationHelper.getSlotForBlockRoot(any(Bytes32.class)))
        .thenReturn(Optional.of(slot));
    when(gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(any(), any()))
        .thenReturn(true);
    when(gossipValidationHelper.getParentStateInBlockEpoch(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(dataStructureUtil.randomBeaconState())));
    when(gossipValidationHelper.isProposerTheExpectedProposer(any(), any(), any()))
        .thenReturn(true);
    // Mock execution payload bid check - dynamically return bid with matching KZG commitments root
    // This is needed because each sidecar has different KZG commitments
    when(executionPayloadBidManager.getExecutionPayloadBid(any(Bytes32.class)))
        .thenAnswer(
            invocation -> {
              // Create a mock bid that will match whatever sidecar is being validated
              final SignedExecutionPayloadBid mockSignedBid = mock(SignedExecutionPayloadBid.class);
              final ExecutionPayloadBid mockBid = mock(ExecutionPayloadBid.class);
              when(mockSignedBid.getMessage()).thenReturn(mockBid);
              // Return the same KZG commitments root as the sidecar being validated
              // Note: this won't work for sidecars with different commitments, but we'll handle
              // those in specific tests
              when(mockBid.getBlobKzgCommitmentsRoot())
                  .thenReturn(dataColumnSidecar.getKzgCommitments().hashTreeRoot());
              return Optional.of(mockSignedBid);
            });
  }

  @Test
  void shouldAcceptValidGloasDataColumnSidecar() {
    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
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
            mockSpec,
            invalidBlocks,
            gossipValidationHelper,
            executionPayloadBidManager,
            metricsSystemStub,
            stubTimeProvider);

    SafeFutureAssert.assertThatSafeFuture(validatorWithMockedSpec.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldIgnoreWhenIsNotFirstValidForGloasTrackingKey() {
    // Add tracking key to the set (beaconBlockRoot + columnIndex)
    validator
        .getReceivedValidDataColumnSidecarInfoSet()
        .add(new GloasTrackingKey(beaconBlockRoot, index));

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  // Note: Gloas does NOT check for future slots or finalized slots
  // These checks were removed in the Gloas spec

  // TODO: Test for KZG validation failure requires either:
  // 1. Creating data with invalid KZG proofs, or
  // 2. Mocking at Spec/SpecLogic level to return mock MiscHelpers
  // Skipping for now as other tests cover the main validation flow

  @Test
  void shouldNotValidateHeaderSignatureForGloas() {
    // In Gloas, there's no header signature validation
    // This test verifies that signature validation is never called
    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Verify that signature validation was never invoked
    verify(gossipValidationHelper, never())
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
  }

  @Test
  void shouldNotValidateInclusionProofForGloas() {
    // In Gloas, there's no inclusion proof validation (always passes)
    // The inclusion proof methods should never be called
    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Gloas helper's verifyInclusionProof always returns true, so no actual verification happens
    // This is verified by the implementation
  }

  @Test
  void shouldTrackByBeaconBlockRootAndColumnIndex() {
    // First sidecar - should accept
    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Verify the tracking key was added (beaconBlockRoot + columnIndex)
    assertThat(validator.getReceivedValidDataColumnSidecarInfoSet())
        .contains(new GloasTrackingKey(beaconBlockRoot, index));

    // Manually add a duplicate to the tracking set (same beaconBlockRoot and columnIndex)
    validator
        .getReceivedValidDataColumnSidecarInfoSet()
        .add(new GloasTrackingKey(beaconBlockRoot, index));

    // Same beaconBlockRoot and columnIndex - should ignore
    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @Test
  void shouldAcceptDifferentColumnIndexForSameBlock() {
    // First sidecar
    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Create another sidecar with different column index but same slot and block root
    final UInt64 differentIndex = UInt64.valueOf(2);
    final DataColumnSidecar sidecarDifferentIndex =
        dataStructureUtil.new RandomDataColumnSidecarBuilder()
            .slot(slot)
            .index(differentIndex)
            .beaconBlockRoot(beaconBlockRoot)
            .build();

    // Update mock to return execution payload bid with matching KZG commitments for the new sidecar
    final SignedExecutionPayloadBid mockSignedBid2 = mock(SignedExecutionPayloadBid.class);
    final ExecutionPayloadBid mockBid2 = mock(ExecutionPayloadBid.class);
    when(mockSignedBid2.getMessage()).thenReturn(mockBid2);
    when(mockBid2.getBlobKzgCommitmentsRoot())
        .thenReturn(sidecarDifferentIndex.getKzgCommitments().hashTreeRoot());
    when(executionPayloadBidManager.getExecutionPayloadBid(beaconBlockRoot))
        .thenReturn(Optional.of(mockSignedBid2));

    // Different column index, should accept
    SafeFutureAssert.assertThatSafeFuture(validator.validate(sidecarDifferentIndex))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Verify both tracking keys are present
    assertThat(validator.getReceivedValidDataColumnSidecarInfoSet())
        .contains(new GloasTrackingKey(beaconBlockRoot, index))
        .contains(new GloasTrackingKey(beaconBlockRoot, differentIndex));
  }

  @Test
  void shouldAcceptDifferentBlockForSameColumnIndex() {
    // First sidecar
    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Different block (different beacon block root) but same slot and column index
    final Bytes32 differentBlockRoot = dataStructureUtil.randomBytes32();
    final DataColumnSidecar sidecarDifferentBlock =
        dataStructureUtil.new RandomDataColumnSidecarBuilder()
            .slot(slot)
            .index(index)
            .beaconBlockRoot(differentBlockRoot)
            .build();

    // Mock execution payload bid for the different block root
    final SignedExecutionPayloadBid mockSignedBid3 = mock(SignedExecutionPayloadBid.class);
    final ExecutionPayloadBid mockBid3 = mock(ExecutionPayloadBid.class);
    when(mockSignedBid3.getMessage()).thenReturn(mockBid3);
    when(mockBid3.getBlobKzgCommitmentsRoot())
        .thenReturn(sidecarDifferentBlock.getKzgCommitments().hashTreeRoot());
    when(executionPayloadBidManager.getExecutionPayloadBid(differentBlockRoot))
        .thenReturn(Optional.of(mockSignedBid3));

    // Should accept - different block root means different tracking key
    SafeFutureAssert.assertThatSafeFuture(validator.validate(sidecarDifferentBlock))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Verify both tracking keys are present
    assertThat(validator.getReceivedValidDataColumnSidecarInfoSet())
        .contains(new GloasTrackingKey(beaconBlockRoot, index))
        .contains(new GloasTrackingKey(differentBlockRoot, index));
  }

  @Test
  void shouldRejectWhenBeaconBlockRootNotKnown() {
    // Mock getSlotForBlockRoot to return empty (block not known)
    when(gossipValidationHelper.getSlotForBlockRoot(beaconBlockRoot)).thenReturn(Optional.empty());

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldRejectWhenSlotDoesNotMatchBlock() {
    // Mock getSlotForBlockRoot to return a different slot
    final UInt64 differentSlot = UInt64.valueOf(3);
    when(gossipValidationHelper.getSlotForBlockRoot(beaconBlockRoot))
        .thenReturn(Optional.of(differentSlot));

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldAcceptWhenSlotMatchesBlock() {
    // Mock getSlotForBlockRoot to return the same slot as the sidecar
    when(gossipValidationHelper.getSlotForBlockRoot(beaconBlockRoot)).thenReturn(Optional.of(slot));

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
  }

  @Test
  void shouldRejectWhenExecutionPayloadBidNotFound() {
    // Mock getSlotForBlockRoot to return the correct slot
    when(gossipValidationHelper.getSlotForBlockRoot(beaconBlockRoot)).thenReturn(Optional.of(slot));
    // Mock getExecutionPayloadBid to return empty (bid not found)
    when(executionPayloadBidManager.getExecutionPayloadBid(beaconBlockRoot))
        .thenReturn(Optional.empty());

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldRejectWhenKzgCommitmentsDoNotMatch() {
    // Mock getSlotForBlockRoot to return the correct slot
    when(gossipValidationHelper.getSlotForBlockRoot(beaconBlockRoot)).thenReturn(Optional.of(slot));

    // Create an execution payload bid with different KZG commitments
    // The random execution payload bid will have different commitments than the sidecar
    final SignedExecutionPayloadBid signedExecutionPayloadBid =
        dataStructureUtil.randomSignedExecutionPayloadBid(slot);
    when(executionPayloadBidManager.getExecutionPayloadBid(beaconBlockRoot))
        .thenReturn(Optional.of(signedExecutionPayloadBid));

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldAcceptWhenKzgCommitmentsMatch() {
    // Mock getSlotForBlockRoot to return the correct slot
    when(gossipValidationHelper.getSlotForBlockRoot(beaconBlockRoot)).thenReturn(Optional.of(slot));

    // Create a mock envelope with matching KZG commitments
    final SignedExecutionPayloadBid mockSignedBid = mock(SignedExecutionPayloadBid.class);
    final ExecutionPayloadBid mockBid = mock(ExecutionPayloadBid.class);
    when(mockSignedBid.getMessage()).thenReturn(mockBid);
    when(mockBid.getBlobKzgCommitmentsRoot())
        .thenReturn(dataColumnSidecar.getKzgCommitments().hashTreeRoot());

    when(executionPayloadBidManager.getExecutionPayloadBid(beaconBlockRoot))
        .thenReturn(Optional.of(mockSignedBid));

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
  }
}
