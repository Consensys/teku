/*
 * Copyright Consensys Software Inc., 2026
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
import static tech.pegasys.teku.spec.config.Constants.MAX_SLOTS_TO_TRACK_BUILDERS_BIDS;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
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
  private static final int MIN_BID_INCREMENT_PERCENTAGE = 1;

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
    this.bidValidator =
        new ExecutionPayloadBidGossipValidator(
            spec, gossipValidationHelper, MIN_BID_INCREMENT_PERCENTAGE);

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
  void shouldNotCacheHigherBidIfInvalid() {
    // valid, lower value bid is accepted and cached
    final UInt64 lowerValue = UInt64.valueOf(10000);
    final SignedExecutionPayloadBid lowerValueBid =
        dataStructureUtil.randomSignedExecutionPayloadBid(
            dataStructureUtil.randomExecutionPayloadBid(
                parentBlockHash, slot, builderIndex, lowerValue, UInt64.ZERO));

    // Mock validation for the lower value bid
    when(gossipValidationHelper.isBlockHashKnown(
            parentBlockHash, lowerValueBid.getMessage().getParentBlockRoot()))
        .thenReturn(true);
    when(gossipValidationHelper.getSlotForBlockRoot(
            lowerValueBid.getMessage().getParentBlockRoot()))
        .thenReturn(Optional.of(slot.decrement()));
    when(gossipValidationHelper.getParentStateInBlockEpoch(
            slot.decrement(), lowerValueBid.getMessage().getParentBlockRoot(), slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(postState)));
    when(gossipValidationHelper.isValidBuilderIndex(builderIndex, postState, slot))
        .thenReturn(true);
    when(gossipValidationHelper.builderHasEnoughBalanceForBid(
            lowerValue, builderIndex, postState, slot))
        .thenReturn(true);
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), eq(builderIndex), any(), any()))
        .thenReturn(true);

    assertThatSafeFuture(bidValidator.validate(lowerValueBid)).isCompletedWithValue(ACCEPT);

    // modified copy of the original bid with a higher value (at least 1% higher) and different
    // builder index
    // Add a large increment to ensure it's over threshold (2x MIN_BID_INCREMENT_PERCENTAGE)
    final UInt64 higherValue =
        lowerValue.plus(lowerValue.times(2 * MIN_BID_INCREMENT_PERCENTAGE).dividedBy(100));
    final UInt64 differentBuilderIndex = builderIndex.plus(1);
    final ExecutionPayloadBid originalBidMessage = lowerValueBid.getMessage();
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
    // bad signature to make it fail validation
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), eq(higherValueInvalidBid.getMessage().getBuilderIndex()), any(), any()))
        .thenReturn(false);
    // invalid, higher value bid is rejected
    assertThatSafeFuture(bidValidator.validate(higherValueInvalidBid))
        .isCompletedWithValue(reject("Invalid payload execution bid signature"));

    // valid, intermediate value bid with a value higher than the initially cached one
    // (1.5x MIN_BID_INCREMENT_PERCENTAGE)
    final UInt64 intermediateValue =
        lowerValue.times(200 + (3 * MIN_BID_INCREMENT_PERCENTAGE)).dividedBy(200);
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

  @TestTemplate
  void shouldIgnoreBidWhenBuilderAddedDuringValidation() {
    // Create an incomplete future that we control
    final SafeFuture<Optional<BeaconState>> slowStateFuture = new SafeFuture<>();

    // Mock the first bid's validation to use the slow future
    when(gossipValidationHelper.getParentStateInBlockEpoch(slot.decrement(), parentBlockRoot, slot))
        .thenReturn(slowStateFuture);

    // Start validating the first bid (it will wait on slowStateFuture)
    final SafeFuture<InternalValidationResult> firstBidResult = bidValidator.validate(signedBid);

    // Reset the mock to return completed future for subsequent calls
    when(gossipValidationHelper.getParentStateInBlockEpoch(slot.decrement(), parentBlockRoot, slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(postState)));

    // Validate and complete a second bid from the same builder while first is in flight
    final SignedExecutionPayloadBid secondBid =
        dataStructureUtil.randomSignedExecutionPayloadBid(signedBid.getMessage());
    final SafeFuture<InternalValidationResult> secondBidResult = bidValidator.validate(secondBid);

    // Wait for second bid to fully complete and add builder to set
    assertThatSafeFuture(secondBidResult).isCompletedWithValue(ACCEPT);
    secondBidResult.join();

    // Now complete the first bid's validation - it should hit the race condition check
    slowStateFuture.complete(Optional.of(postState));

    // First bid should be ignored because builder was added by second bid
    assertThatSafeFuture(firstBidResult)
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldEvictOldestSlotAfterMaxSlotsTracked() {
    final UInt64 startSlot = UInt64.valueOf(100);
    final UInt64 sameBuilder = builderIndex;
    final SpecVersion specVersion = mock(SpecVersion.class);
    final MiscHelpers miscHelpers = mock(MiscHelpers.class);
    when(miscHelpers.computeSigningRoot(any(Bytes.class), any(Bytes32.class)))
        .thenReturn(Bytes32.random());
    when(specVersion.miscHelpers()).thenReturn(miscHelpers);
    when(spec.atSlot(any())).thenReturn(specVersion);

    // Submit bids for MAX_SLOTS_TO_TRACK_BUILDERS_BIDS + 1 slots to fill the cache and evict oldest
    // entries
    for (int i = 0; i < MAX_SLOTS_TO_TRACK_BUILDERS_BIDS + 1; i++) {
      final UInt64 bidValue = UInt64.valueOf(1000 + i);
      final SignedExecutionPayloadBid bid =
          dataStructureUtil.randomSignedExecutionPayloadBid(
              dataStructureUtil.randomExecutionPayloadBid(
                  dataStructureUtil.randomBytes32(),
                  startSlot.plus(i),
                  sameBuilder,
                  bidValue,
                  UInt64.ZERO));
      mockBidValidation(bid, sameBuilder, bidValue);
      assertThatSafeFuture(bidValidator.validate(bid)).isCompletedWithValue(ACCEPT);
    }

    // Submit another bid for slot 100 which has been evicted and should be accepted
    final SignedExecutionPayloadBid bidForEvictedSlot =
        dataStructureUtil.randomSignedExecutionPayloadBid(
            dataStructureUtil.randomExecutionPayloadBid(
                dataStructureUtil.randomBytes32(),
                startSlot,
                sameBuilder,
                UInt64.valueOf(2000),
                UInt64.ZERO));
    mockBidValidation(bidForEvictedSlot, sameBuilder, UInt64.valueOf(2000));
    assertThatSafeFuture(bidValidator.validate(bidForEvictedSlot)).isCompletedWithValue(ACCEPT);

    // Submit another bid for most recent slot which is still in cache and should be ignored
    final UInt64 cachedSlot = startSlot.plus(MAX_SLOTS_TO_TRACK_BUILDERS_BIDS);
    final SignedExecutionPayloadBid bidForCachedSlot =
        dataStructureUtil.randomSignedExecutionPayloadBid(
            dataStructureUtil.randomExecutionPayloadBid(
                dataStructureUtil.randomBytes32(),
                cachedSlot,
                sameBuilder,
                UInt64.valueOf(3000),
                UInt64.ZERO));
    mockBidValidation(bidForCachedSlot, sameBuilder, UInt64.valueOf(3000));
    assertThatSafeFuture(bidValidator.validate(bidForCachedSlot))
        .isCompletedWithValue(
            ignore(
                "Already received a bid from builder with index %s at slot %s",
                sameBuilder, cachedSlot));
  }

  @TestTemplate
  void shouldAccept_whenBidMeetsMinimumIncrementThreshold() {
    // First bid with known value
    final UInt64 firstBidValue = UInt64.valueOf(10000);
    final SignedExecutionPayloadBid firstBid =
        dataStructureUtil.randomSignedExecutionPayloadBid(
            dataStructureUtil.randomExecutionPayloadBid(
                parentBlockHash, slot, builderIndex, firstBidValue, UInt64.ZERO));
    mockBidValidation(firstBid, builderIndex, firstBidValue);
    assertThatSafeFuture(bidValidator.validate(firstBid)).isCompletedWithValue(ACCEPT);

    // Second bid at exactly MIN_BID_INCREMENT_PERCENTAGE higher
    final UInt64 secondBidValue =
        firstBidValue.times(100 + MIN_BID_INCREMENT_PERCENTAGE).dividedBy(100);
    final UInt64 differentBuilderIndex = builderIndex.plus(1);
    final SignedExecutionPayloadBid secondBid =
        dataStructureUtil.randomSignedExecutionPayloadBid(
            dataStructureUtil.randomExecutionPayloadBid(
                parentBlockHash, slot, differentBuilderIndex, secondBidValue, UInt64.ZERO));

    mockBidValidation(secondBid, differentBuilderIndex, secondBidValue);
    assertThatSafeFuture(bidValidator.validate(secondBid)).isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldIgnore_whenBidBelowMinimumIncrementThreshold() {
    // First bid with known value
    final UInt64 firstBidValue = UInt64.valueOf(10000);
    final SignedExecutionPayloadBid firstBid =
        dataStructureUtil.randomSignedExecutionPayloadBid(
            dataStructureUtil.randomExecutionPayloadBid(
                parentBlockHash, slot, builderIndex, firstBidValue, UInt64.ZERO));
    mockBidValidation(firstBid, builderIndex, firstBidValue);
    assertThatSafeFuture(bidValidator.validate(firstBid)).isCompletedWithValue(ACCEPT);

    // Second bid at half the threshold percentage (below MIN_BID_INCREMENT_PERCENTAGE threshold)
    final UInt64 secondBidValue =
        firstBidValue.times(200 + MIN_BID_INCREMENT_PERCENTAGE).dividedBy(200);
    final UInt64 differentBuilderIndex = builderIndex.plus(1);
    final SignedExecutionPayloadBid secondBid =
        dataStructureUtil.randomSignedExecutionPayloadBid(
            dataStructureUtil.randomExecutionPayloadBid(
                parentBlockHash, slot, differentBuilderIndex, secondBidValue, UInt64.ZERO));

    // Calculate what the minimum required bid would be
    final UInt64 minIncrement = firstBidValue.times(MIN_BID_INCREMENT_PERCENTAGE).dividedBy(100);
    final UInt64 minRequiredBid = firstBidValue.plus(minIncrement);

    mockBidValidation(secondBid, differentBuilderIndex, secondBidValue);
    assertThatSafeFuture(bidValidator.validate(secondBid))
        .isCompletedWithValue(
            ignore(
                "Bid value %s does not meet minimum increment threshold (%s%%). Current highest: %s, minimum required: %s",
                secondBidValue, MIN_BID_INCREMENT_PERCENTAGE, firstBidValue, minRequiredBid));
  }

  @TestTemplate
  void shouldAccept_whenBidWellAboveMinimumIncrementThreshold() {
    // First bid with known value
    final UInt64 firstBidValue = UInt64.valueOf(10000);
    final SignedExecutionPayloadBid firstBid =
        dataStructureUtil.randomSignedExecutionPayloadBid(
            dataStructureUtil.randomExecutionPayloadBid(
                parentBlockHash, slot, builderIndex, firstBidValue, UInt64.ZERO));
    mockBidValidation(firstBid, builderIndex, firstBidValue);
    assertThatSafeFuture(bidValidator.validate(firstBid)).isCompletedWithValue(ACCEPT);

    // Second bid at 5x the threshold percentage (well above MIN_BID_INCREMENT_PERCENTAGE threshold)
    final UInt64 secondBidValue =
        firstBidValue.times(100 + (5 * MIN_BID_INCREMENT_PERCENTAGE)).dividedBy(100);
    final UInt64 differentBuilderIndex = builderIndex.plus(1);
    final SignedExecutionPayloadBid secondBid =
        dataStructureUtil.randomSignedExecutionPayloadBid(
            dataStructureUtil.randomExecutionPayloadBid(
                parentBlockHash, slot, differentBuilderIndex, secondBidValue, UInt64.ZERO));

    mockBidValidation(secondBid, differentBuilderIndex, secondBidValue);
    assertThatSafeFuture(bidValidator.validate(secondBid)).isCompletedWithValue(ACCEPT);
  }

  private void mockBidValidation(
      final SignedExecutionPayloadBid bid, final UInt64 builderIndex, final UInt64 bidValue) {
    final ExecutionPayloadBid message = bid.getMessage();
    final UInt64 slot = message.getSlot();
    when(gossipValidationHelper.isSlotCurrentOrNext(slot)).thenReturn(true);
    when(gossipValidationHelper.isBlockHashKnown(
            message.getParentBlockHash(), message.getParentBlockRoot()))
        .thenReturn(true);
    when(gossipValidationHelper.getSlotForBlockRoot(message.getParentBlockRoot()))
        .thenReturn(Optional.of(slot.decrement()));
    when(gossipValidationHelper.getParentStateInBlockEpoch(
            slot.decrement(), message.getParentBlockRoot(), slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(postState)));
    when(gossipValidationHelper.isValidBuilderIndex(builderIndex, postState, slot))
        .thenReturn(true);
    when(gossipValidationHelper.builderHasEnoughBalanceForBid(
            bidValue, builderIndex, postState, slot))
        .thenReturn(true);
  }
}
