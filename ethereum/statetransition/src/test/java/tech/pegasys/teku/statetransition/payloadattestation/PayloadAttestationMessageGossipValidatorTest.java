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

package tech.pegasys.teku.statetransition.payloadattestation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

@TestSpecContext(milestone = {SpecMilestone.GLOAS})
public class PayloadAttestationMessageGossipValidatorTest {
  private final Spec spec = mock(Spec.class);
  private final Map<Bytes32, BlockImportResult> invalidBlockRoots = new HashMap<>();
  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);

  private PayloadAttestationMessageGossipValidator payloadAttestationMessageGossipValidator;

  private PayloadAttestationMessage payloadAttestationMessage;
  private UInt64 slot;
  private UInt64 validatorIndex;
  private Bytes32 blockRoot;
  private BeaconState postState;

  @BeforeEach
  void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    DataStructureUtil dataStructureUtil = specContext.getDataStructureUtil();
    this.payloadAttestationMessageGossipValidator =
        new PayloadAttestationMessageGossipValidator(
            spec, gossipValidationHelper, invalidBlockRoots);

    payloadAttestationMessage = dataStructureUtil.randomPayloadAttestationMessage();
    slot = payloadAttestationMessage.getData().getSlot();
    validatorIndex = payloadAttestationMessage.getValidatorIndex();
    blockRoot = payloadAttestationMessage.getData().getBeaconBlockRoot();
    postState = dataStructureUtil.randomBeaconState();

    when(gossipValidationHelper.isForCurrentSlot(slot)).thenReturn(true);
    when(gossipValidationHelper.isBlockAvailable(blockRoot)).thenReturn(true);
    when(gossipValidationHelper.getStateAtSlotAndBlockRoot(new SlotAndBlockRoot(slot, blockRoot)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(postState)));
    final SpecVersion specVersion = mock(SpecVersion.class);
    final MiscHelpers miscHelpers = mock(MiscHelpers.class);
    final Bytes32 signingRoot = Bytes32.random();
    when(miscHelpers.computeSigningRoot(eq(payloadAttestationMessage.getData()), any()))
        .thenReturn(signingRoot);
    when(specVersion.miscHelpers()).thenReturn(miscHelpers);
    when(spec.atSlot(slot)).thenReturn(specVersion);
    when(spec.getPtc(postState, slot)).thenReturn(IntList.of(validatorIndex.intValue()));
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), any(), any(), any()))
        .thenReturn(true);
  }

  @TestTemplate
  void shouldAccept() {
    SafeFutureAssert.assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(payloadAttestationMessage))
        .isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldIgnore_whenSlotIsNotCurrent() {
    when(gossipValidationHelper.isForCurrentSlot(slot)).thenReturn(false);
    SafeFutureAssert.assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(payloadAttestationMessage))
        .isCompletedWithValue(IGNORE);
  }

  @TestTemplate
  void shouldIgnore_whenAlreadySeen() {
    SafeFutureAssert.assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(payloadAttestationMessage))
        .isCompletedWithValue(ACCEPT);
    SafeFutureAssert.assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(payloadAttestationMessage))
        .isCompletedWithValue(IGNORE);
  }

  @TestTemplate
  void shouldSaveForFuture_whenBlockNotAvailable() {
    when(gossipValidationHelper.isBlockAvailable(blockRoot)).thenReturn(false);
    SafeFutureAssert.assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(payloadAttestationMessage))
        .isCompletedWithValue(SAVE_FOR_FUTURE);
  }

  @TestTemplate
  void shouldReject_whenBlockIsInvalid() {
    invalidBlockRoots.put(blockRoot, BlockImportResult.FAILED_INVALID_ANCESTRY);
    SafeFutureAssert.assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(payloadAttestationMessage))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldSaveForFuture_whenStateIsUnavailable() {
    when(gossipValidationHelper.getStateAtSlotAndBlockRoot(new SlotAndBlockRoot(slot, blockRoot)))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    SafeFutureAssert.assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(payloadAttestationMessage))
        .isCompletedWithValue(SAVE_FOR_FUTURE);
  }

  @TestTemplate
  void shouldReject_whenValidatorNotInPtcCommittee() {
    when(spec.getPtc(postState, slot)).thenReturn(IntList.of());
    SafeFutureAssert.assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(payloadAttestationMessage))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldReject_whenSignatureIsInvalid() {
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), any(), any(), any()))
        .thenReturn(false);
    SafeFutureAssert.assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(payloadAttestationMessage))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldSkipSeenPayloadAttestation() {
    // First validation is successful
    SafeFutureAssert.assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(payloadAttestationMessage))
        .isCompletedWithValue(ACCEPT);
    verify(gossipValidationHelper).isBlockAvailable(blockRoot);
    verify(gossipValidationHelper).getStateAtSlotAndBlockRoot(any());
    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());

    clearInvocations(gossipValidationHelper);

    // Second validation is ignored
    SafeFutureAssert.assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(payloadAttestationMessage))
        .isCompletedWithValue(IGNORE);
    verify(gossipValidationHelper, never()).isBlockAvailable(blockRoot);
    verify(gossipValidationHelper, never()).getStateAtSlotAndBlockRoot(any());
    verify(gossipValidationHelper, never())
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
  }

  @TestTemplate
  void shouldNotMarkAsSeenIfValidationFails() {
    // Fail validation due to bad signature
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), any(), any(), any()))
        .thenReturn(false);
    SafeFutureAssert.assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(payloadAttestationMessage))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);

    // Fix the signature and try again
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), any(), any(), any()))
        .thenReturn(true);

    // It should be accepted now
    SafeFutureAssert.assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(payloadAttestationMessage))
        .isCompletedWithValue(ACCEPT);
  }
}
