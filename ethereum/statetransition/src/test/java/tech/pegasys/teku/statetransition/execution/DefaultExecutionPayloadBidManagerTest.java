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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
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
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadBidGossipValidator;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.store.UpdatableStore;

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

  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final UpdatableStore store = mock(UpdatableStore.class);
  private final ForkChoiceStrategy forkChoiceStrategy = mock(ForkChoiceStrategy.class);

  private final DefaultExecutionPayloadBidManager executionPayloadBidManager =
      new DefaultExecutionPayloadBidManager(
          spec,
          executionPayloadBidGossipValidator,
          receivedExecutionPayloadBidEventsChannelPublisher,
          recentChainData);

  @BeforeEach
  public void setUp() {
    when(recentChainData.getStore()).thenReturn(store);
    when(store.getForkChoiceStrategy()).thenReturn(forkChoiceStrategy);
    when(forkChoiceStrategy.shouldExtendPayload(eq(store), any())).thenReturn(false);
  }

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

    final Optional<SignedExecutionPayloadBid> maybeSignedBid =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                state, SafeFuture.completedFuture(getPayloadResponse), blockProductionPerformance));

    assertThat(maybeSignedBid).isPresent();

    final SignedExecutionPayloadBid signedBid = maybeSignedBid.get();

    assertThat(signedBid.getSignature()).isEqualTo(BLSSignature.infinity());

    final ExecutionPayloadBid bid = signedBid.getMessage();

    final SszList<SszKZGCommitment> expectedBlobKzgCommitments =
        schemaDefinitions.getBlobKzgCommitmentsSchema().createFromBlobsBundle(blobsBundle);
    final ExecutionPayloadBid expectedBid =
        schemaDefinitions
            .getExecutionPayloadBidSchema()
            .createLocalSelfBuiltBid(
                // should_extend_payload returns false
                state.getLatestExecutionPayloadBid().getParentBlockHash(),
                state.getLatestBlockHeader().getRoot(),
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

    final Optional<SignedExecutionPayloadBid> maybeSignedBid =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                state, SafeFuture.completedFuture(getPayloadResponse), blockProductionPerformance));

    assertThat(maybeSignedBid).isPresent();

    final ExecutionPayloadBid bid = maybeSignedBid.orElseThrow().getMessage();
    final SszList<SszKZGCommitment> expectedBlobKzgCommitments =
        schemaDefinitions.getBlobKzgCommitmentsSchema().createFromBlobsBundle(blobsBundle);
    final ExecutionPayloadBid expectedBid =
        schemaDefinitions
            .getExecutionPayloadBidSchema()
            .createLocalSelfBuiltBid(
                // bootstrap fallback should still extend from the latest bid block hash
                state.getLatestExecutionPayloadBid().getBlockHash(),
                state.getLatestBlockHeader().getRoot(),
                state.getSlot(),
                executionPayload,
                expectedBlobKzgCommitments,
                executionRequests.hashTreeRoot());

    assertThat(bid).isEqualTo(expectedBid);
  }
}
