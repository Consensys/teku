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

package tech.pegasys.teku.statetransition.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager.RemoteBidOrigin;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadBidGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class DefaultExecutionPayloadBidManagerTest {

  private final Spec spec = TestSpecFactory.createMainnetGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final BlockProductionPerformance blockProductionPerformance =
      mock(BlockProductionPerformance.class);

  private final ExecutionPayloadBidGossipValidator executionPayloadBidGossipValidator =
      mock(ExecutionPayloadBidGossipValidator.class);

  private final ReceivedExecutionPayloadBidEventsChannel
      receivedExecutionPayloadBidEventsChannelPublisher =
          mock(ReceivedExecutionPayloadBidEventsChannel.class);

  private final DefaultExecutionPayloadBidManager executionPayloadBidManager =
      new DefaultExecutionPayloadBidManager(
          spec,
          executionPayloadBidGossipValidator,
          receivedExecutionPayloadBidEventsChannelPublisher);

  @Test
  public void createsLocalBidForBlock() {
    final BeaconStateGloas state = BeaconStateGloas.required(dataStructureUtil.randomBeaconState());

    final ExecutionPayload executionPayload =
        dataStructureUtil.randomExecutionPayload(state.getSlot());
    final BlobsBundle blobsBundle = dataStructureUtil.randomBlobsBundle(3);

    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(state.getSlot()).getSchemaDefinitions());

    final ExecutionRequests executionRequests = dataStructureUtil.randomExecutionRequests();

    final GetPayloadResponse getPayloadResponse =
        new GetPayloadResponse(
            executionPayload,
            UInt256.valueOf(1000000000000L),
            blobsBundle,
            false,
            executionRequests);

    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = executionPayload.getParentHash();

    final SignedExecutionPayloadBid signedBid =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                parentRoot,
                parentBlockHash,
                state,
                SafeFuture.completedFuture(getPayloadResponse),
                blockProductionPerformance));

    assertThat(signedBid.getSignature()).isEqualTo(BLSSignature.infinity());

    final ExecutionPayloadBid bid = signedBid.getMessage();

    final SszList<SszKZGCommitment> expectedBlobKzgCommitments =
        schemaDefinitions.getBlobKzgCommitmentsSchema().createFromBlobsBundle(blobsBundle);
    final ExecutionPayloadBid expectedBid =
        schemaDefinitions
            .getExecutionPayloadBidSchema()
            .createLocalSelfBuiltBid(
                parentRoot,
                state.getSlot(),
                executionPayload,
                expectedBlobKzgCommitments,
                executionRequests.hashTreeRoot());

    assertThat(bid).isEqualTo(expectedBid);

    // verify event is triggered to subscribers
    verify(receivedExecutionPayloadBidEventsChannelPublisher)
        .onExecutionPayloadBidValidated(signedBid);
  }

  @Test
  public void createsLocalBidForBlock_whenBootstrapBidHasZeroParentBlockHash() {
    final BeaconStateGloas originalState =
        BeaconStateGloas.required(dataStructureUtil.randomBeaconState());
    final ExecutionPayloadBid latestExecutionPayloadBid =
        originalState.getLatestExecutionPayloadBid();
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(
            spec.atSlot(originalState.getSlot()).getSchemaDefinitions());

    final BeaconStateGloas state =
        BeaconStateGloas.required(
            originalState.updated(
                mutableState ->
                    MutableBeaconStateGloas.required(mutableState)
                        .setLatestExecutionPayloadBid(
                            schemaDefinitions
                                .getExecutionPayloadBidSchema()
                                .create(
                                    Bytes32.ZERO,
                                    latestExecutionPayloadBid.getParentBlockRoot(),
                                    latestExecutionPayloadBid.getBlockHash(),
                                    latestExecutionPayloadBid.getPrevRandao(),
                                    latestExecutionPayloadBid.getFeeRecipient(),
                                    latestExecutionPayloadBid.getGasLimit(),
                                    latestExecutionPayloadBid.getBuilderIndex(),
                                    latestExecutionPayloadBid.getSlot(),
                                    latestExecutionPayloadBid.getValue(),
                                    latestExecutionPayloadBid.getExecutionPayment(),
                                    latestExecutionPayloadBid.getBlobKzgCommitments(),
                                    latestExecutionPayloadBid.getExecutionRequestsRoot()))));

    final ExecutionPayload executionPayload =
        dataStructureUtil.randomExecutionPayload(state.getSlot());
    final BlobsBundle blobsBundle = dataStructureUtil.randomBlobsBundle(3);
    final ExecutionRequests executionRequests = dataStructureUtil.randomExecutionRequests();

    final GetPayloadResponse getPayloadResponse =
        new GetPayloadResponse(
            executionPayload,
            UInt256.valueOf(1000000000000L),
            blobsBundle,
            false,
            executionRequests);

    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = executionPayload.getParentHash();

    final ExecutionPayloadBid bid =
        SafeFutureAssert.safeJoin(
                executionPayloadBidManager.getBidForBlock(
                    parentRoot,
                    parentBlockHash,
                    state,
                    SafeFuture.completedFuture(getPayloadResponse),
                    blockProductionPerformance))
            .getMessage();

    final SszList<SszKZGCommitment> expectedBlobKzgCommitments =
        schemaDefinitions.getBlobKzgCommitmentsSchema().createFromBlobsBundle(blobsBundle);
    final ExecutionPayloadBid expectedBid =
        schemaDefinitions
            .getExecutionPayloadBidSchema()
            .createLocalSelfBuiltBid(
                parentRoot,
                state.getSlot(),
                executionPayload,
                expectedBlobKzgCommitments,
                executionRequests.hashTreeRoot());

    assertThat(bid).isEqualTo(expectedBid);
  }

  @Test
  public void rejectsLocalBidWhenPayloadParentHashDoesNotMatchProductionParent() {
    final BeaconStateGloas state = BeaconStateGloas.required(dataStructureUtil.randomBeaconState());
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 selectedParentBlockHash = dataStructureUtil.randomBytes32();
    final Bytes32 payloadParentBlockHash = dataStructureUtil.randomBytes32();

    SafeFutureAssert.assertThatSafeFuture(
            executionPayloadBidManager.getBidForBlock(
                parentRoot,
                selectedParentBlockHash,
                state,
                SafeFuture.completedFuture(
                    randomGetPayloadResponse(state.getSlot(), payloadParentBlockHash)),
                blockProductionPerformance))
        .isCompletedExceptionallyWith(IllegalStateException.class)
        .hasMessageContaining("does not match selected production parent execution hash");
  }

  @Test
  public void returnsHighestValueRemoteBidForBlock() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();

    final SignedExecutionPayloadBid lowerBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));
    final SignedExecutionPayloadBid higherBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(500));
    final SignedExecutionPayloadBid mediumBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(250));

    addAcceptedBid(lowerBid);
    addAcceptedBid(higherBid);
    addAcceptedBid(mediumBid);

    final BeaconStateGloas state = stateAtSlot(slot);

    final SignedExecutionPayloadBid signedBid =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                parentRoot,
                parentBlockHash,
                state,
                new SafeFuture<>(),
                blockProductionPerformance));

    assertThat(signedBid).isEqualTo(higherBid);
  }

  @Test
  public void filtersRemoteBidsByParentBlockRoot() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final Bytes32 otherParentRoot = dataStructureUtil.randomBytes32();

    final SignedExecutionPayloadBid bidForOtherParent =
        createBid(slot, otherParentRoot, parentBlockHash, UInt64.valueOf(1_000_000));
    final SignedExecutionPayloadBid bidForOurParent =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));

    addAcceptedBid(bidForOtherParent);
    addAcceptedBid(bidForOurParent);

    verify(receivedExecutionPayloadBidEventsChannelPublisher)
        .onExecutionPayloadBidValidated(bidForOtherParent);
    verify(receivedExecutionPayloadBidEventsChannelPublisher)
        .onExecutionPayloadBidValidated(bidForOurParent);

    final BeaconStateGloas state = stateAtSlot(slot);

    final SignedExecutionPayloadBid signedBid =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                parentRoot,
                parentBlockHash,
                state,
                new SafeFuture<>(),
                blockProductionPerformance));

    assertThat(signedBid).isEqualTo(bidForOurParent);
  }

  @Test
  public void filtersRemoteBidsByParentBlockHash() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final Bytes32 otherParentBlockHash = dataStructureUtil.randomBytes32();

    final SignedExecutionPayloadBid bidForOtherParentHash =
        createBid(slot, parentRoot, otherParentBlockHash, UInt64.valueOf(1_000_000));
    final SignedExecutionPayloadBid bidForOurParentHash =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));

    addAcceptedBid(bidForOtherParentHash);
    addAcceptedBid(bidForOurParentHash);

    final BeaconStateGloas state = stateAtSlot(slot);

    final SignedExecutionPayloadBid signedBid =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                parentRoot,
                parentBlockHash,
                state,
                new SafeFuture<>(),
                blockProductionPerformance));

    assertThat(signedBid).isEqualTo(bidForOurParentHash);
  }

  @Test
  public void fallsBackToLocalSelfBuiltBidWhenNoRemoteBidMatches() {
    final BeaconStateGloas state = BeaconStateGloas.required(dataStructureUtil.randomBeaconState());
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();

    // remote bids for a different slot or parent should not match
    addAcceptedBid(
        createBid(state.getSlot().plus(5), parentRoot, parentBlockHash, UInt64.valueOf(100)));
    addAcceptedBid(
        createBid(
            state.getSlot(),
            dataStructureUtil.randomBytes32(),
            parentBlockHash,
            UInt64.valueOf(100)));

    final SignedExecutionPayloadBid signedBid =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                parentRoot,
                parentBlockHash,
                state,
                SafeFuture.completedFuture(
                    randomGetPayloadResponse(state.getSlot(), parentBlockHash)),
                blockProductionPerformance));

    assertThat(signedBid.getSignature()).isEqualTo(BLSSignature.infinity());
  }

  @Test
  public void doesNotStoreBidWhenValidationDoesNotAccept() {
    final SignedExecutionPayloadBid signedBid =
        createBid(UInt64.valueOf(10), dataStructureUtil.randomBytes32(), UInt64.valueOf(100));

    when(executionPayloadBidGossipValidator.validate(signedBid))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("nope")));

    SafeFutureAssert.safeJoin(
        executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.P2P));

    verify(receivedExecutionPayloadBidEventsChannelPublisher, never())
        .onExecutionPayloadBidValidated(signedBid);
  }

  @Test
  public void onSlotPrunesBidsForPriorSlots() {
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final UInt64 currentSlot = UInt64.valueOf(10);

    final SignedExecutionPayloadBid staleBid =
        createBid(currentSlot.minus(1), parentRoot, parentBlockHash, UInt64.valueOf(500));
    final SignedExecutionPayloadBid currentSlotBid =
        createBid(currentSlot, parentRoot, parentBlockHash, UInt64.valueOf(200));
    final SignedExecutionPayloadBid nextSlotBid =
        createBid(currentSlot.plus(1), parentRoot, parentBlockHash, UInt64.valueOf(300));

    addAcceptedBid(staleBid);
    addAcceptedBid(currentSlotBid);
    addAcceptedBid(nextSlotBid);

    executionPayloadBidManager.onSlot(currentSlot);

    // stale bid is pruned: lookup falls back to a local self-built bid
    final UInt64 staleSlot = staleBid.getMessage().getSlot();
    final SignedExecutionPayloadBid staleLookup =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                parentRoot,
                parentBlockHash,
                stateAtSlot(staleSlot),
                SafeFuture.completedFuture(randomGetPayloadResponse(staleSlot, parentBlockHash)),
                blockProductionPerformance));
    assertThat(staleLookup.getSignature()).isEqualTo(BLSSignature.infinity());

    // current and next slot bids are retained
    assertThat(
            SafeFutureAssert.safeJoin(
                executionPayloadBidManager.getBidForBlock(
                    parentRoot,
                    parentBlockHash,
                    stateAtSlot(currentSlot),
                    new SafeFuture<>(),
                    blockProductionPerformance)))
        .isEqualTo(currentSlotBid);
    assertThat(
            SafeFutureAssert.safeJoin(
                executionPayloadBidManager.getBidForBlock(
                    parentRoot,
                    parentBlockHash,
                    stateAtSlot(currentSlot.plus(1)),
                    new SafeFuture<>(),
                    blockProductionPerformance)))
        .isEqualTo(nextSlotBid);
  }

  private GetPayloadResponse randomGetPayloadResponse(
      final UInt64 slot, final Bytes32 parentBlockHash) {
    return new GetPayloadResponse(
        dataStructureUtil.randomExecutionPayload(
            slot, builder -> builder.parentHash(parentBlockHash)),
        UInt256.valueOf(1000000000000L),
        dataStructureUtil.randomBlobsBundle(3),
        false,
        dataStructureUtil.randomExecutionRequests());
  }

  private SignedExecutionPayloadBid createBid(
      final UInt64 slot, final Bytes32 parentBlockRoot, final UInt64 value) {
    return createBid(slot, parentBlockRoot, dataStructureUtil.randomBytes32(), value);
  }

  private SignedExecutionPayloadBid createBid(
      final UInt64 slot,
      final Bytes32 parentBlockRoot,
      final Bytes32 parentBlockHash,
      final UInt64 value) {
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());
    final ExecutionPayloadBidSchema schema = schemaDefinitions.getExecutionPayloadBidSchema();
    final ExecutionPayloadBid bid =
        schema.create(
            parentBlockHash,
            parentBlockRoot,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomEth1Address(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            slot,
            value,
            UInt64.ZERO,
            dataStructureUtil.randomBlobKzgCommitments(),
            dataStructureUtil.randomBytes32());
    return schemaDefinitions
        .getSignedExecutionPayloadBidSchema()
        .create(bid, dataStructureUtil.randomSignature());
  }

  private void addAcceptedBid(final SignedExecutionPayloadBid signedBid) {
    when(executionPayloadBidGossipValidator.validate(signedBid))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));
    SafeFutureAssert.safeJoin(
        executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.P2P));
  }

  private BeaconStateGloas stateAtSlot(final UInt64 slot) {
    return BeaconStateGloas.required(
        dataStructureUtil.randomBeaconState().updated(state -> state.setSlot(slot)));
  }
}
