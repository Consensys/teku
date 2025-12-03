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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = {SpecMilestone.GLOAS})
public class ExecutionPayloadBidGossipValidatorTest {
  private final Spec spec = mock(Spec.class);
  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);
  private ExecutionPayloadBidGossipValidator bidValidator;
  private DataStructureUtil dataStructureUtil;
  private SignedExecutionPayloadBid signedBid;
  private ExecutionPayloadBid bid;
  private UInt64 slot;
  private UInt64 builderIndex;
  private Bytes32 parentBlockRoot;
  private Bytes32 parentBlockHash;
  private BeaconState postState;

  @BeforeEach
  void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    this.dataStructureUtil = specContext.getDataStructureUtil();
    this.bidValidator = new ExecutionPayloadBidGossipValidator(spec, gossipValidationHelper);

    signedBid = dataStructureUtil.randomSignedExecutionPayloadBid(UInt64.ZERO);
    bid = signedBid.getMessage();
    slot = bid.getSlot();
    builderIndex = bid.getBuilderIndex();
    parentBlockRoot = bid.getParentBlockRoot();
    parentBlockHash = bid.getParentBlockHash();
    postState = dataStructureUtil.randomBeaconState();

    when(gossipValidationHelper.isSlotCurrentOrNext(slot)).thenReturn(true);
    when(gossipValidationHelper.isBlockHashKnown(parentBlockHash, parentBlockRoot))
        .thenReturn(true);
    when(gossipValidationHelper.getSlotForBlockRoot(parentBlockRoot))
        .thenReturn(Optional.of(slot.decrement()));
    when(gossipValidationHelper.getParentStateInBlockEpoch(slot.decrement(), parentBlockRoot, slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(postState)));
    when(gossipValidationHelper.isValidBuilderIndex(builderIndex, postState, slot))
        .thenReturn(true);
    when(gossipValidationHelper.hasBuilderWithdrawalCredential(builderIndex, postState, slot))
        .thenReturn(true);
    when(gossipValidationHelper.builderHasEnoughBalanceForBid(
            bid.getValue(), builderIndex, postState, slot))
        .thenReturn(true);
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(true);
    final SpecVersion specVersion = mock(SpecVersion.class);
    final MiscHelpers miscHelpers = mock(MiscHelpers.class);
    final Bytes32 signingRoot = Bytes32.random();
    when(miscHelpers.computeSigningRoot(eq(signedBid.getMessage()), any())).thenReturn(signingRoot);
    when(specVersion.miscHelpers()).thenReturn(miscHelpers);
    when(spec.atSlot(slot)).thenReturn(specVersion);
    when(spec.getActiveValidatorIndices(postState, slot))
        .thenReturn(IntList.of(builderIndex.intValue()));
  }

  @TestTemplate
  void shouldAccept() {
    assertThatSafeFuture(bidValidator.validate(signedBid)).isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldReject_whenExecutionPaymentIsNonZero() {
    final SignedExecutionPayloadBid bidWithPayment =
        dataStructureUtil.randomSignedExecutionPayloadBid(UInt64.ONE);
    assertThatSafeFuture(bidValidator.validate(bidWithPayment))
        .isCompletedWithValue(
            reject(
                "Bid's execution payment should be 0 but was %s",
                bidWithPayment.getMessage().getExecutionPayment()));
  }

  @TestTemplate
  void shouldIgnore_whenSlotIsNotCurrentOrNext() {
    when(gossipValidationHelper.isSlotCurrentOrNext(slot)).thenReturn(false);
    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(
            ignore("Bid must be for current or next slot but was for slot %s", slot));
  }

  @TestTemplate
  void shouldIgnore_whenAlreadySeen() {
    assertThatSafeFuture(bidValidator.validate(signedBid)).isCompletedWithValue(ACCEPT);
    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(
            ignore(
                "Already received a bid from builder with index %s at slot %s",
                builderIndex, slot));
  }

  @TestTemplate
  void shouldIgnore_whenBidValueIsLowerThanSeen() {
    // a higher value bid is accepted
    assertThatSafeFuture(bidValidator.validate(signedBid)).isCompletedWithValue(ACCEPT);

    // a lower value bid from a different builder with the same parent block hash
    final UInt64 lowerBidValue = bid.getValue().minus(1);
    final SignedExecutionPayloadBid lowerValueBid =
        dataStructureUtil.randomSignedExecutionPayloadBid(
            dataStructureUtil.randomExecutionPayloadBid(
                parentBlockHash,
                slot,
                builderIndex.plus(1),
                lowerBidValue,
                bid.getExecutionPayment()));

    assertThatSafeFuture(bidValidator.validate(lowerValueBid))
        .isCompletedWithValue(
            ignore(
                "Already received a bid with a higher value %s for block with parent hash %s. Current bid's value is %s",
                bid.getValue(), parentBlockHash, lowerBidValue));
  }

  @TestTemplate
  void shouldNotCacheHigherBidIfInvalid() {
    // valid, lower value bid is accepted and cached
    final UInt64 lowerValue = signedBid.getMessage().getValue();
    assertThatSafeFuture(bidValidator.validate(signedBid)).isCompletedWithValue(ACCEPT);

    // modified copy of the original bid with a higher value and different builder index
    final UInt64 higherValue = lowerValue.plus(10);
    final UInt64 differentBuilderIndex = builderIndex.plus(1);
    final ExecutionPayloadBid originalBidMessage = signedBid.getMessage();
    final ExecutionPayloadBid higherValueBidMessage =
        originalBidMessage
            .getSchema()
            .create(
                originalBidMessage.getParentBlockHash(),
                originalBidMessage.getParentBlockRoot(),
                originalBidMessage.getBlockHash(),
                originalBidMessage.getPrevRandao(),
                originalBidMessage.getFeeRecipient(),
                originalBidMessage.getGasLimit(),
                differentBuilderIndex,
                originalBidMessage.getSlot(),
                higherValue,
                originalBidMessage.getExecutionPayment(),
                originalBidMessage.getBlobKzgCommitmentsRoot());

    final SignedExecutionPayloadBid higherValueInvalidBid =
        dataStructureUtil.randomSignedExecutionPayloadBid(higherValueBidMessage);

    when(gossipValidationHelper.isValidBuilderIndex(differentBuilderIndex, postState, slot))
        .thenReturn(true);
    when(gossipValidationHelper.builderHasEnoughBalanceForBid(
            higherValue, differentBuilderIndex, postState, slot))
        .thenReturn(true);
    when(gossipValidationHelper.hasBuilderWithdrawalCredential(
            differentBuilderIndex, postState, slot))
        .thenReturn(true);
    // bad signature to make it fail validation
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), eq(higherValueInvalidBid.getMessage().getBuilderIndex()), any(), any()))
        .thenReturn(false);
    // invalid, higher value bid is rejected
    assertThatSafeFuture(bidValidator.validate(higherValueInvalidBid))
        .isCompletedWithValue(reject("Invalid payload execution bid signature"));

    // valid, intermediate value bid with a higher value that the initially cached one
    final UInt64 intermediateValue = lowerValue.plus(5);
    final UInt64 intermediateBidBuilderIndex = builderIndex.plus(2);
    final SignedExecutionPayloadBid intermediateValueValidBid =
        dataStructureUtil.randomSignedExecutionPayloadBid(
            dataStructureUtil.randomExecutionPayloadBid(
                parentBlockHash,
                slot,
                intermediateBidBuilderIndex,
                intermediateValue,
                UInt64.ZERO));

    when(gossipValidationHelper.isBlockHashKnown(
            parentBlockHash, intermediateValueValidBid.getMessage().getParentBlockRoot()))
        .thenReturn(true);
    when(gossipValidationHelper.isValidBuilderIndex(intermediateBidBuilderIndex, postState, slot))
        .thenReturn(true);
    when(gossipValidationHelper.getSlotForBlockRoot(
            intermediateValueValidBid.getMessage().getParentBlockRoot()))
        .thenReturn(Optional.of(slot));
    when(gossipValidationHelper.builderHasEnoughBalanceForBid(
            intermediateValue, intermediateBidBuilderIndex, postState, slot))
        .thenReturn(true);
    when(gossipValidationHelper.getParentStateInBlockEpoch(
            slot, intermediateValueValidBid.getMessage().getParentBlockRoot(), slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(postState)));
    when(gossipValidationHelper.hasBuilderWithdrawalCredential(
            intermediateBidBuilderIndex, postState, slot))
        .thenReturn(true);
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), eq(intermediateValueValidBid.getMessage().getBuilderIndex()), any(), any()))
        .thenReturn(true);

    // the intermediate value bid is accepted
    assertThatSafeFuture(bidValidator.validate(intermediateValueValidBid))
        .isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldSaveForFuture_whenParentBlockHashIsUnknown() {
    when(gossipValidationHelper.isBlockHashKnown(parentBlockHash, parentBlockRoot))
        .thenReturn(false);
    assertThatSafeFuture(bidValidator.validate(signedBid)).isCompletedWithValue(SAVE_FOR_FUTURE);
  }

  @TestTemplate
  void shouldSaveForFuture_whenParentBlockIsNotAvailable() {
    when(gossipValidationHelper.getSlotForBlockRoot(parentBlockRoot)).thenReturn(Optional.empty());
    assertThatSafeFuture(bidValidator.validate(signedBid)).isCompletedWithValue(SAVE_FOR_FUTURE);
  }

  @TestTemplate
  void shouldSaveForFuture_whenStateIsUnavailable() {
    when(gossipValidationHelper.getParentStateInBlockEpoch(slot.decrement(), parentBlockRoot, slot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    assertThatSafeFuture(bidValidator.validate(signedBid)).isCompletedWithValue(SAVE_FOR_FUTURE);
  }

  @TestTemplate
  void shouldReject_whenBuilderIndexIsInvalid() {
    when(gossipValidationHelper.isValidBuilderIndex(builderIndex, postState, slot))
        .thenReturn(false);
    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(
            reject(
                "Invalid builder index %s. Builder should be valid, active and non-slashed.",
                builderIndex));
  }

  @TestTemplate
  void shouldReject_whenBuilderLacksBuilderWithdrawalCredential() {
    when(gossipValidationHelper.hasBuilderWithdrawalCredential(builderIndex, postState, slot))
        .thenReturn(false);
    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(
            reject("Builder with index %s must have builder withdrawal credential", builderIndex));
  }

  @TestTemplate
  void shouldIgnore_whenBuilderHasInsufficientBalance() {
    when(gossipValidationHelper.builderHasEnoughBalanceForBid(
            bid.getValue(), builderIndex, postState, slot))
        .thenReturn(false);
    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(
            ignore(
                "Bid value %s exceeds builder with index %s excess balance",
                bid.getValue(), builderIndex));
  }

  @TestTemplate
  void shouldReject_whenSignatureIsInvalid() {
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(false);
    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(reject("Invalid payload execution bid signature"));
  }

  @TestTemplate
  void shouldIgnoreSeenBid() {
    assertThatSafeFuture(bidValidator.validate(signedBid)).isCompletedWithValue(ACCEPT);
    verify(gossipValidationHelper).isBlockHashKnown(parentBlockHash, parentBlockRoot);
    verify(gossipValidationHelper).getParentStateInBlockEpoch(any(), any(), any());
    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToBuilderIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);

    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(
            ignore(
                "Already received a bid from builder with index %s at slot %s",
                builderIndex, slot));
  }

  @TestTemplate
  void shouldNotMarkAsSeenIfValidationFails() {
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(false);
    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(reject("Invalid payload execution bid signature"));

    // Fix the signature mock and try again
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(true);

    // It should be accepted now, not ignored
    assertThatSafeFuture(bidValidator.validate(signedBid)).isCompletedWithValue(ACCEPT);
  }
}
