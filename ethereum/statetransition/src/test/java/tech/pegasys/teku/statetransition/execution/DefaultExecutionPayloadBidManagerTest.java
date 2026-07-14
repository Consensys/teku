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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
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
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
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
          receivedExecutionPayloadBidEventsChannelPublisher,
          UInt64.valueOf(90),
          true);

  @Test
  public void createsLocalBidForBlock() {
    final BeaconStateGloas state = BeaconStateGloas.required(dataStructureUtil.randomBeaconState());

    final ExecutionPayload executionPayload =
        dataStructureUtil.randomExecutionPayload(state.getSlot());
    final BlobsBundle blobsBundle = dataStructureUtil.randomBlobsBundle(3);

    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(state.getSlot()).getSchemaDefinitions());

    final ExecutionRequests executionRequests =
        dataStructureUtil.randomExecutionRequests(state.getSlot());

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
                Optional.empty(),
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
    final ExecutionRequests executionRequests =
        dataStructureUtil.randomExecutionRequests(state.getSlot());

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
                    Optional.empty(),
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
                Optional.empty(),
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
                SafeFuture.failedFuture(new RuntimeException("engine unavailable")),
                Optional.empty(),
                blockProductionPerformance));

    assertThat(signedBid).isEqualTo(higherBid);
  }

  @Test
  void requestedFactorOverridesConfiguredFactorAndSelectsLocal() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));
    addAcceptedBid(remoteBid);

    final SignedExecutionPayloadBid selectedBid =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                parentRoot,
                parentBlockHash,
                stateAtSlot(slot),
                SafeFuture.completedFuture(
                    getPayloadResponse(
                        slot, parentBlockHash, UInt256.valueOf(80_000_000_000L), false)),
                Optional.of(UInt64.valueOf(80)),
                blockProductionPerformance));

    assertThat(selectedBid.getSignature()).isEqualTo(BLSSignature.infinity());
    verify(blockProductionPerformance, never()).builderBidValidated();
  }

  @Test
  void configuredFactorSelectsRemoteBelowThreshold() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));
    addAcceptedBid(remoteBid);

    final SignedExecutionPayloadBid selectedBid =
        selectBid(
            executionPayloadBidManager,
            remoteBid,
            parentRoot,
            parentBlockHash,
            UInt256.valueOf(89_000_000_000L),
            false,
            Optional.empty());

    assertThat(selectedBid).isEqualTo(remoteBid);
    verify(blockProductionPerformance, times(1)).builderBidValidated();
  }

  @Test
  void configuredFactorSelectsLocalAtThreshold() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));
    addAcceptedBid(remoteBid);

    final SignedExecutionPayloadBid selectedBid =
        selectBid(
            executionPayloadBidManager,
            remoteBid,
            parentRoot,
            parentBlockHash,
            UInt256.valueOf(90_000_000_000L),
            false,
            Optional.empty());

    assertThat(selectedBid.getSignature()).isEqualTo(BLSSignature.infinity());
  }

  @Test
  void preferExecutionFactorSelectsViableLocalBid() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.MAX_VALUE);
    addAcceptedBid(remoteBid);

    final SignedExecutionPayloadBid selectedBid =
        selectBid(
            executionPayloadBidManager,
            remoteBid,
            parentRoot,
            parentBlockHash,
            UInt256.ZERO,
            false,
            Optional.of(UInt64.ZERO));

    assertThat(selectedBid.getSignature()).isEqualTo(BLSSignature.infinity());
  }

  @Test
  void preferBuilderFactorSelectsRemoteBid() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.ONE);
    addAcceptedBid(remoteBid);

    final SignedExecutionPayloadBid selectedBid =
        selectBid(
            executionPayloadBidManager,
            remoteBid,
            parentRoot,
            parentBlockHash,
            UInt256.MAX_VALUE,
            false,
            Optional.of(UInt64.MAX_VALUE));

    assertThat(selectedBid).isEqualTo(remoteBid);
  }

  @Test
  void comparesLocalValueAtWeiPrecision() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.ONE);
    addAcceptedBid(remoteBid);

    final SignedExecutionPayloadBid belowThreshold =
        selectBid(
            executionPayloadBidManager,
            remoteBid,
            parentRoot,
            parentBlockHash,
            UInt256.valueOf(899_999_999),
            false,
            Optional.empty());
    final SignedExecutionPayloadBid atThreshold =
        selectBid(
            executionPayloadBidManager,
            remoteBid,
            parentRoot,
            parentBlockHash,
            UInt256.valueOf(900_000_000),
            false,
            Optional.empty());

    assertThat(belowThreshold).isEqualTo(remoteBid);
    assertThat(atThreshold.getSignature()).isEqualTo(BLSSignature.infinity());
  }

  @Test
  void enabledShouldOverrideBuilderSelectsLowValueLocalBid() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));
    addAcceptedBid(remoteBid);

    final SignedExecutionPayloadBid selectedBid =
        selectBid(
            executionPayloadBidManager,
            remoteBid,
            parentRoot,
            parentBlockHash,
            UInt256.ONE,
            true,
            Optional.empty());

    assertThat(selectedBid.getSignature()).isEqualTo(BLSSignature.infinity());
  }

  @Test
  void disabledShouldOverrideBuilderIgnoresOverrideFlag() {
    final DefaultExecutionPayloadBidManager manager = createManager(UInt64.valueOf(90), false);
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));
    addAcceptedBid(manager, remoteBid);

    final SignedExecutionPayloadBid selectedBid =
        selectBid(
            manager, remoteBid, parentRoot, parentBlockHash, UInt256.ONE, true, Optional.empty());

    assertThat(selectedBid).isEqualTo(remoteBid);
  }

  @Test
  void selectsRemoteBidWhenLocalPayloadFutureFails() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));
    addAcceptedBid(remoteBid);

    final SignedExecutionPayloadBid selectedBid =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                parentRoot,
                parentBlockHash,
                stateAtSlot(slot),
                SafeFuture.failedFuture(new RuntimeException("engine unavailable")),
                Optional.of(UInt64.ZERO),
                blockProductionPerformance));

    assertThat(selectedBid).isEqualTo(remoteBid);
  }

  @Test
  void selectsRemoteBidWhenLocalPayloadHasWrongParentHash() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));
    addAcceptedBid(remoteBid);

    final SignedExecutionPayloadBid selectedBid =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                parentRoot,
                parentBlockHash,
                stateAtSlot(slot),
                SafeFuture.completedFuture(
                    randomGetPayloadResponse(slot, dataStructureUtil.randomBytes32())),
                Optional.of(UInt64.ZERO),
                blockProductionPerformance));

    assertThat(selectedBid).isEqualTo(remoteBid);
  }

  @Test
  void selectsRemoteBidWhenLocalPayloadIsMissingGloasData() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));
    addAcceptedBid(remoteBid);
    final ExecutionPayload payload =
        dataStructureUtil.randomExecutionPayload(
            slot, builder -> builder.parentHash(parentBlockHash));
    final GetPayloadResponse missingBlobs = mock(GetPayloadResponse.class);
    when(missingBlobs.getExecutionPayload()).thenReturn(payload);
    when(missingBlobs.getExecutionPayloadValue()).thenReturn(UInt256.MAX_VALUE);
    when(missingBlobs.getBlobsBundle()).thenReturn(Optional.empty());
    when(missingBlobs.getExecutionRequests())
        .thenReturn(Optional.of(dataStructureUtil.randomExecutionRequests(slot)));
    final GetPayloadResponse malformedBlobs = mock(GetPayloadResponse.class);
    final BlobsBundle malformedBundle = mock(BlobsBundle.class);
    when(malformedBundle.getCommitments())
        .thenThrow(new IllegalStateException("malformed blobs bundle"));
    when(malformedBlobs.getExecutionPayload()).thenReturn(payload);
    when(malformedBlobs.getExecutionPayloadValue()).thenReturn(UInt256.MAX_VALUE);
    when(malformedBlobs.getBlobsBundle()).thenReturn(Optional.of(malformedBundle));
    when(malformedBlobs.getExecutionRequests())
        .thenReturn(Optional.of(dataStructureUtil.randomExecutionRequests(slot)));

    final SignedExecutionPayloadBid missingBoth =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                parentRoot,
                parentBlockHash,
                stateAtSlot(slot),
                SafeFuture.completedFuture(new GetPayloadResponse(payload, UInt256.MAX_VALUE)),
                Optional.of(UInt64.ZERO),
                blockProductionPerformance));
    final SignedExecutionPayloadBid missingRequests =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                parentRoot,
                parentBlockHash,
                stateAtSlot(slot),
                SafeFuture.completedFuture(
                    new GetPayloadResponse(
                        payload, UInt256.MAX_VALUE, dataStructureUtil.randomBlobsBundle(3), true)),
                Optional.of(UInt64.ZERO),
                blockProductionPerformance));
    final SignedExecutionPayloadBid onlyMissingBlobs =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                parentRoot,
                parentBlockHash,
                stateAtSlot(slot),
                SafeFuture.completedFuture(missingBlobs),
                Optional.of(UInt64.ZERO),
                blockProductionPerformance));
    final SignedExecutionPayloadBid malformedBlobsBundle =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                parentRoot,
                parentBlockHash,
                stateAtSlot(slot),
                SafeFuture.completedFuture(malformedBlobs),
                Optional.of(UInt64.ZERO),
                blockProductionPerformance));

    assertThat(missingBoth).isEqualTo(remoteBid);
    assertThat(missingRequests).isEqualTo(remoteBid);
    assertThat(onlyMissingBlobs).isEqualTo(remoteBid);
    assertThat(malformedBlobsBundle).isEqualTo(remoteBid);
  }

  @Test
  void logsComparisonFactorAndSource() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100_000_000));
    addAcceptedBid(remoteBid);

    try (final LogCaptor logCaptor = LogCaptor.forClass(DefaultExecutionPayloadBidManager.class)) {
      selectBid(
          executionPayloadBidManager,
          remoteBid,
          parentRoot,
          parentBlockHash,
          UInt256.valueOf(80_000_000_000_000_000L),
          false,
          Optional.of(UInt64.valueOf(80)));
      selectBid(
          executionPayloadBidManager,
          remoteBid,
          parentRoot,
          parentBlockHash,
          UInt256.valueOf(89_000_000_000_000_000L),
          false,
          Optional.empty());

      assertThat(logCaptor.getInfoLogs())
          .anyMatch(
              log ->
                  log.contains(
                      "Local execution payload (0.080000 ETH) is chosen over remote bid (0.100000 ETH)"))
          .anyMatch(log -> log.contains("builder compare factor: 80%."))
          .anyMatch(
              log ->
                  log.contains(
                      "Remote bid (0.100000 ETH) is chosen over local execution payload (0.089000 ETH)"))
          .anyMatch(log -> log.contains("builder compare factor: 90%."));
    }
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
                SafeFuture.failedFuture(new RuntimeException("engine unavailable")),
                Optional.empty(),
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
                SafeFuture.failedFuture(new RuntimeException("engine unavailable")),
                Optional.empty(),
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
                Optional.empty(),
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
                Optional.empty(),
                blockProductionPerformance));
    assertThat(staleLookup.getSignature()).isEqualTo(BLSSignature.infinity());

    // current and next slot bids are retained
    assertThat(
            SafeFutureAssert.safeJoin(
                executionPayloadBidManager.getBidForBlock(
                    parentRoot,
                    parentBlockHash,
                    stateAtSlot(currentSlot),
                    SafeFuture.failedFuture(new RuntimeException("engine unavailable")),
                    Optional.empty(),
                    blockProductionPerformance)))
        .isEqualTo(currentSlotBid);
    assertThat(
            SafeFutureAssert.safeJoin(
                executionPayloadBidManager.getBidForBlock(
                    parentRoot,
                    parentBlockHash,
                    stateAtSlot(currentSlot.plus(1)),
                    SafeFuture.failedFuture(new RuntimeException("engine unavailable")),
                    Optional.empty(),
                    blockProductionPerformance)))
        .isEqualTo(nextSlotBid);
  }

  private GetPayloadResponse randomGetPayloadResponse(
      final UInt64 slot, final Bytes32 parentBlockHash) {
    return getPayloadResponse(slot, parentBlockHash, UInt256.valueOf(1_000_000_000_000L), false);
  }

  private GetPayloadResponse getPayloadResponse(
      final UInt64 slot,
      final Bytes32 parentBlockHash,
      final UInt256 value,
      final boolean shouldOverrideBuilder) {
    return new GetPayloadResponse(
        dataStructureUtil.randomExecutionPayload(
            slot, builder -> builder.parentHash(parentBlockHash)),
        value,
        dataStructureUtil.randomBlobsBundle(3),
        shouldOverrideBuilder,
        dataStructureUtil.randomExecutionRequests(slot));
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
    addAcceptedBid(executionPayloadBidManager, signedBid);
  }

  private void addAcceptedBid(
      final DefaultExecutionPayloadBidManager manager, final SignedExecutionPayloadBid signedBid) {
    when(executionPayloadBidGossipValidator.validate(signedBid))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));
    SafeFutureAssert.safeJoin(manager.validateAndAddBid(signedBid, RemoteBidOrigin.P2P));
  }

  private SignedExecutionPayloadBid selectBid(
      final DefaultExecutionPayloadBidManager manager,
      final SignedExecutionPayloadBid remoteBid,
      final Bytes32 parentRoot,
      final Bytes32 parentBlockHash,
      final UInt256 localValue,
      final boolean shouldOverrideBuilder,
      final Optional<UInt64> requestedBuilderBoostFactor) {
    return SafeFutureAssert.safeJoin(
        manager.getBidForBlock(
            parentRoot,
            parentBlockHash,
            stateAtSlot(remoteBid.getMessage().getSlot()),
            SafeFuture.completedFuture(
                getPayloadResponse(
                    remoteBid.getMessage().getSlot(),
                    parentBlockHash,
                    localValue,
                    shouldOverrideBuilder)),
            requestedBuilderBoostFactor,
            blockProductionPerformance));
  }

  private DefaultExecutionPayloadBidManager createManager(
      final UInt64 builderBidCompareFactor, final boolean useShouldOverrideBuilderFlag) {
    return new DefaultExecutionPayloadBidManager(
        spec,
        executionPayloadBidGossipValidator,
        receivedExecutionPayloadBidEventsChannelPublisher,
        builderBidCompareFactor,
        useShouldOverrideBuilderFlag);
  }

  private BeaconStateGloas stateAtSlot(final UInt64 slot) {
    return BeaconStateGloas.required(
        dataStructureUtil.randomBeaconState().updated(state -> state.setSlot(slot)));
  }
}
