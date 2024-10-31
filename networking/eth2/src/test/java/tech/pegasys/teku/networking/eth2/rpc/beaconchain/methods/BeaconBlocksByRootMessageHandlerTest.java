/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestApproval;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class BeaconBlocksByRootMessageHandlerTest {
  private static final RpcEncoding RPC_ENCODING =
      RpcEncoding.createSszSnappyEncoding(
          TestSpecFactory.createDefault().getNetworkingConfig().getMaxChunkSize());

  private static final String V2_PROTOCOL_ID =
      BeaconChainMethodIds.getBlocksByRootMethodId(2, RPC_ENCODING);

  private final UInt64 altairForkEpoch = UInt64.ONE;
  private final Spec spec = TestSpecFactory.createMinimalWithAltairForkEpoch(altairForkEpoch);
  private final UInt64 altairForkSlot = spec.computeStartSlotAtEpoch(altairForkEpoch);
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
  private final ChainUpdater chainUpdater = storageSystem.chainUpdater();
  final UpdatableStore store = mock(UpdatableStore.class);
  final RecentChainData recentChainData = mock(RecentChainData.class);
  final BeaconBlocksByRootMessageHandler handler =
      new BeaconBlocksByRootMessageHandler(spec, storageSystem.getMetricsSystem(), recentChainData);
  final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  final ResponseCallback<SignedBeaconBlock> callback = mock(ResponseCallback.class);

  private final Optional<RequestApproval> allowedObjectsRequest =
      Optional.of(
          new RequestApproval.RequestApprovalBuilder().objectsCount(100).timeSeconds(ZERO).build());

  @BeforeEach
  public void setup() {
    chainUpdater.initializeGenesis();
    when(peer.approveRequest()).thenReturn(true);
    when(peer.approveBlocksRequest(any(), anyLong())).thenReturn(allowedObjectsRequest);
    when(recentChainData.getRecentlyValidatedSignedBlockByRoot(any())).thenReturn(Optional.empty());
    // Forward block requests from the mock to the actual store
    when(recentChainData.retrieveSignedBlockByRoot(any()))
        .thenAnswer(
            i -> storageSystem.recentChainData().getStore().retrieveSignedBlock(i.getArgument(0)));
    when(callback.respond(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  public void validateRequest_shouldRejectRequestWhenCountIsTooBig() {
    final UInt64 denebForkEpoch = UInt64.valueOf(4);
    // Testing for Deneb since can't initialize BeaconBlocksByRootMessageHandler with size more
    // than MAX_REQUEST_BLOCKS
    final Spec spec = TestSpecFactory.createMinimalWithDenebForkEpoch(denebForkEpoch);

    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(denebForkEpoch));

    final BeaconBlocksByRootMessageHandler handler =
        new BeaconBlocksByRootMessageHandler(
            spec, storageSystem.getMetricsSystem(), recentChainData);

    final SpecConfig config = spec.forMilestone(SpecMilestone.DENEB).getConfig();
    final SpecConfigDeneb specConfigDeneb = SpecConfigDeneb.required(config);
    final List<Bytes32> roots =
        UInt64.range(
                UInt64.ZERO, UInt64.valueOf(specConfigDeneb.getMaxRequestBlocksDeneb()).increment())
            .map(__ -> Bytes32.ZERO)
            .collect(Collectors.toList());

    final Optional<RpcException> result =
        handler.validateRequest(
            V2_PROTOCOL_ID,
            new BeaconBlocksByRootRequestMessage(
                spec.getGenesisSchemaDefinitions().getBeaconBlocksByRootRequestMessageSchema(),
                roots));

    assertThat(result)
        .hasValue(
            new RpcException(
                INVALID_REQUEST_CODE, "Only a maximum of 128 blocks can be requested per request"));
  }

  @Test
  public void onIncomingMessage_respondsWithAllBlocks() {
    final List<SignedBeaconBlock> blocks = buildChain(5);

    final BeaconBlocksByRootRequestMessage message = createRequest(blocks);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    // Requesting 5 blocks
    verify(peer, times(1)).approveBlocksRequest(any(), eq(Long.valueOf(blocks.size())));
    // Sending 5 blocks: No rate limiter adjustment required
    verify(peer, never()).adjustBlocksRequest(any(), anyLong());

    for (SignedBeaconBlock block : blocks) {
      verify(recentChainData).retrieveSignedBlockByRoot(block.getRoot());
      verify(callback).respond(block);
    }
  }

  @Test
  public void onIncomingMessage_interruptedByClosedStream() {
    final List<SignedBeaconBlock> blocks = buildChain(5);

    // Mock callback to appear to be closed
    doThrow(new StreamClosedException()).when(callback).respond(any());

    final BeaconBlocksByRootRequestMessage message = createRequest(blocks);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    // Requesting 5 blocks
    verify(peer, times(1)).approveBlocksRequest(any(), eq(Long.valueOf(blocks.size())));
    // Request cancelled
    verify(peer, times(1))
        .adjustBlocksRequest(eq(allowedObjectsRequest.get()), eq(Long.valueOf(0)));

    // Check that we only asked for the first block
    verify(recentChainData, times(1)).retrieveSignedBlockByRoot(any());
    verify(callback, times(1)).respond(any());
  }

  @Test
  public void onIncomingMessage_requestBlocksAcrossAltairFork_v2() {
    // Set up request that spans the altair fork
    chainUpdater.advanceChain(altairForkSlot.minus(3));
    final List<SignedBeaconBlock> blocks = buildChain(5);
    assertThat(blocks.get(0).getSlot().isLessThan(altairForkSlot)).isTrue();
    assertThat(blocks.get(4).getSlot().isGreaterThan(altairForkSlot)).isTrue();
    final BeaconBlocksByRootRequestMessage message = createRequest(blocks);

    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    // Requesting 5 blocks
    verify(peer, times(1)).approveBlocksRequest(any(), eq(Long.valueOf(blocks.size())));
    // Sending 5 blocks: No rate limiter adjustment required
    verify(peer, never()).adjustBlocksRequest(any(), anyLong());

    for (SignedBeaconBlock block : blocks) {
      verify(recentChainData).retrieveSignedBlockByRoot(block.getRoot());
      verify(callback).respond(block);
    }
  }

  @Test
  public void validateResponse_phase0Spec_v2Request() {
    final Optional<RpcException> result =
        handler.validateResponse(V2_PROTOCOL_ID, chainUpdater.advanceChain(5).getBlock());

    assertThat(result).isEmpty();
  }

  @Test
  public void validateResponse_altairSpec_v2RequestForPhase0Block() {
    final Optional<RpcException> result =
        handler.validateResponse(V2_PROTOCOL_ID, chainUpdater.advanceChain(5).getBlock());

    assertThat(result).isEmpty();
  }

  @Test
  public void validateResponse_altairSpec_v2RequestForAltairBlock() {
    final Optional<RpcException> result =
        handler.validateResponse(
            V2_PROTOCOL_ID, chainUpdater.advanceChain(altairForkSlot.plus(1)).getBlock());

    assertThat(result).isEmpty();
  }

  private BeaconBlocksByRootRequestMessage createRequest(final List<SignedBeaconBlock> forBlocks) {
    final List<Bytes32> blockHashes =
        forBlocks.stream().map(SignedBeaconBlock::getRoot).collect(Collectors.toList());
    return new BeaconBlocksByRootRequestMessage(
        spec.getGenesisSchemaDefinitions().getBeaconBlocksByRootRequestMessageSchema(),
        blockHashes);
  }

  private List<SignedBeaconBlock> buildChain(final int chainSize) {
    // Create some blocks to request
    final UInt64 latestSlot = storageSystem.chainBuilder().getLatestSlot();
    chainUpdater.advanceChainUntil(latestSlot.plus(chainSize));

    return storageSystem
        .chainBuilder()
        .streamBlocksAndStates(latestSlot.plus(1))
        .map(SignedBlockAndState::getBlock)
        .collect(Collectors.toList());
  }
}
