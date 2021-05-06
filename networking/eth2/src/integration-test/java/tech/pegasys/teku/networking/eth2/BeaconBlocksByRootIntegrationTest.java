/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.DeserializationFailedException;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.phase0.BeaconBlockBodyPhase0;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BeaconBlocksByRootIntegrationTest extends AbstractRpcMethodIntegrationTest {

  @Test
  public void shouldSendEmptyResponseWhenNoBlocksAreAvailable() throws Exception {
    final Eth2Peer peer = createPeer();
    final List<SignedBeaconBlock> response = requestBlocks(peer, singletonList(Bytes32.ZERO));
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldReturnSingleBlockWhenOnlyOneMatches() throws Exception {
    final Eth2Peer peer = createPeer();
    final SignedBeaconBlock block = addBlock();

    final List<SignedBeaconBlock> response =
        requestBlocks(peer, singletonList(block.getMessage().hashTreeRoot()));
    assertThat(response).containsExactly(block);
  }

  @Test
  public void requestBlocksByRootAfterPeerDisconnectedImmediately() throws RpcException {
    final Eth2Peer peer = createPeer();
    final SignedBeaconBlock block = addBlock();
    final Bytes32 blockHash = block.getMessage().hashTreeRoot();

    peer.disconnectImmediately(Optional.empty(), false);
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer.requestBlocksByRoot(List.of(blockHash), RpcResponseListener.from(blocks::add));

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(blocks).isEmpty();
  }

  @Test
  public void requestBlocksByRootAfterPeerDisconnected()
      throws RpcException, InterruptedException, ExecutionException, TimeoutException {
    final Eth2Peer peer = createPeer();
    final SignedBeaconBlock block = addBlock();
    final Bytes32 blockHash = block.getMessage().hashTreeRoot();

    Waiter.waitFor(peer.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS));
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer.requestBlocksByRoot(List.of(blockHash), RpcResponseListener.from(blocks::add));

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(blocks).isEmpty();
  }

  @Test
  public void requestBlockByRootAfterPeerDisconnectedImmediately() {
    final Eth2Peer peer = createPeer();
    final SignedBeaconBlock block = addBlock();
    final Bytes32 blockHash = block.getMessage().hashTreeRoot();

    peer.disconnectImmediately(Optional.empty(), false);
    final SafeFuture<Optional<SignedBeaconBlock>> res = peer.requestBlockByRoot(blockHash);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
  }

  @Test
  public void requestBlockByRootAfterPeerDisconnected()
      throws InterruptedException, ExecutionException, TimeoutException {
    final Eth2Peer peer = createPeer();
    final SignedBeaconBlock block = addBlock();
    final Bytes32 blockHash = block.getMessage().hashTreeRoot();

    Waiter.waitFor(peer.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS));
    final SafeFuture<Optional<SignedBeaconBlock>> res = peer.requestBlockByRoot(blockHash);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
  }

  @Test
  public void shouldReturnMultipleBlocksWhenAllRequestsMatch() throws Exception {
    final Eth2Peer peer = createPeer();
    final List<SignedBeaconBlock> blocks = asList(addBlock(), addBlock(), addBlock());
    final List<Bytes32> blockRoots =
        blocks.stream()
            .map(SignedBeaconBlock::getMessage)
            .map(BeaconBlock::hashTreeRoot)
            .collect(toList());
    final List<SignedBeaconBlock> response = requestBlocks(peer, blockRoots);
    assertThat(response).containsExactlyElementsOf(blocks);
  }

  @Test
  public void shouldReturnMultipleLargeBlocksWhenAllRequestsMatch() throws Exception {
    final Eth2Peer peer = createPeer();
    final List<SignedBeaconBlock> blocks = largeBlockSequence(3);
    final List<Bytes32> blockRoots =
        blocks.stream().map(SignedBeaconBlock::getRoot).collect(toList());
    final List<SignedBeaconBlock> response = requestBlocks(peer, blockRoots);
    assertThat(response).containsExactlyElementsOf(blocks);
  }

  @Test
  public void shouldReturnMatchingBlocksWhenSomeRequestsDoNotMatch() throws Exception {
    final Eth2Peer peer = createPeer();
    final List<SignedBeaconBlock> blocks = asList(addBlock(), addBlock(), addBlock());

    // Real block roots interspersed with ones that don't match any blocks
    final List<Bytes32> blockRoots =
        blocks.stream()
            .map(SignedBeaconBlock::getMessage)
            .map(BeaconBlock::hashTreeRoot)
            .flatMap(hash -> Stream.of(Bytes32.fromHexStringLenient("0x123456789"), hash))
            .collect(toList());

    final List<SignedBeaconBlock> response = requestBlocks(peer, blockRoots);
    assertThat(response).containsExactlyElementsOf(blocks);
  }

  @Test
  void requestBlockByRoot_shouldReturnEmptyWhenBlockIsNotKnown() throws Exception {
    final Eth2Peer peer = createPeer();
    final Optional<SignedBeaconBlock> result =
        waitFor(peer.requestBlockByRoot(Bytes32.fromHexStringLenient("0x123456789")));
    assertThat(result).isEmpty();
  }

  @ParameterizedTest(name = "enableAltairLocally={0}, enableAltairRemotely={1}")
  @MethodSource("altairVersioningOptions")
  public void requestBlockByRoot_withDisparateVersionsEnabled_requestPhase0Blocks(
      final boolean enableAltairLocally, final boolean enableAltairRemotely) throws Exception {
    final Eth2Peer peer = createPeer(enableAltairLocally, enableAltairRemotely);

    // Setup chain
    final SignedBlockAndState block1 = peerStorage.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = peerStorage.chainUpdater().advanceChain();
    peerStorage.chainUpdater().updateBestBlock(block2);
    assertThat(block1.getBlock().getMessage().getBody()).isInstanceOf(BeaconBlockBodyPhase0.class);
    assertThat(block1.getBlock().getMessage().getBody()).isInstanceOf(BeaconBlockBodyPhase0.class);

    final List<SignedBeaconBlock> response =
        requestBlocks(peer, List.of(block1.getRoot(), block2.getRoot()));
    assertThat(response).containsExactly(block1.getBlock(), block2.getBlock());
  }

  @ParameterizedTest(name = "enableAltairLocally={0}, enableAltairRemotely={1}")
  @MethodSource("altairVersioningOptions")
  public void requestBlockBySlot_withDisparateVersionsEnabled_requestAltairBlocks(
      final boolean enableAltairLocally, final boolean enableAltairRemotely) throws Exception {
    setupPeerStorage(true);
    final Eth2Peer peer = createPeer(enableAltairLocally, enableAltairRemotely);

    // Setup chain
    peerStorage.chainUpdater().advanceChain(altairSlot.minus(1));
    // Create altair blocks
    final SignedBlockAndState block1 = peerStorage.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = peerStorage.chainUpdater().advanceChain();
    peerStorage.chainUpdater().updateBestBlock(block2);
    assertThat(block1.getBlock().getMessage().getBody()).isInstanceOf(BeaconBlockBodyAltair.class);
    assertThat(block2.getBlock().getMessage().getBody()).isInstanceOf(BeaconBlockBodyAltair.class);

    peerStorage.chainUpdater().updateBestBlock(block2);

    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer.requestBlocksByRoot(
            List.of(block1.getRoot(), block2.getRoot()), RpcResponseListener.from(blocks::add));

    waitFor(() -> assertThat(res).isDone());

    if (enableAltairLocally && enableAltairRemotely) {
      // We should receive a successful response
      assertThat(res).isCompleted();
      assertThat(blocks).containsExactly(block1.getBlock(), block2.getBlock());
    } else if (!enableAltairLocally && enableAltairRemotely) {
      // The peer should refuse to return any results because we're asking for altair blocks using
      // a v1 request
      assertThat(res).isCompletedExceptionally();
      assertThatThrownBy(res::get)
          .hasCauseInstanceOf(RpcException.class)
          .hasMessageContaining("[Code 1] Must request altair blocks using v2 protocol");
    } else {
      // Remote only supports v1, we should get a v1 response back and error out trying to
      // decode these blocks with a phase0 decoder
      assertThat(res).isCompletedExceptionally();
      assertThatThrownBy(res::get).hasCauseInstanceOf(DeserializationFailedException.class);
    }
  }

  public static Stream<Arguments> altairVersioningOptions() {
    return Stream.of(
        Arguments.of(true, true),
        Arguments.of(false, true),
        Arguments.of(true, false),
        Arguments.of(false, false));
  }

  private SignedBeaconBlock addBlock() {
    return peerStorage.chainUpdater().advanceChain().getBlock();
  }

  private List<SignedBeaconBlock> largeBlockSequence(final int count) {
    DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final SignedBeaconBlock parent = peerStorage.chainBuilder().getLatestBlockAndState().getBlock();
    List<SignedBlockAndState> newBlocks =
        dataStructureUtil.randomSignedBlockAndStateSequence(parent, count, true);
    newBlocks.forEach(peerStorage.chainUpdater()::saveBlock);

    return newBlocks.stream().map(SignedBlockAndState::getBlock).collect(Collectors.toList());
  }

  private List<SignedBeaconBlock> requestBlocks(final Eth2Peer peer, final List<Bytes32> blockRoots)
      throws InterruptedException, java.util.concurrent.ExecutionException,
          java.util.concurrent.TimeoutException, RpcException {
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    waitFor(peer.requestBlocksByRoot(blockRoots, RpcResponseListener.from(blocks::add)));
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    return blocks;
  }
}
