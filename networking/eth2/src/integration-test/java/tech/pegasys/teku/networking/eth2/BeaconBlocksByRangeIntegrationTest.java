/*
 * Copyright ConsenSys Software Inc., 2022
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.DeserializationFailedException;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.BeaconBlockBodyEip4844;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.phase0.BeaconBlockBodyPhase0;

public class BeaconBlocksByRangeIntegrationTest extends AbstractRpcMethodIntegrationTest {

  @Test
  public void shouldSendEmptyResponsePreGenesisEvent() throws Exception {
    final Eth2Peer peer = createPeer();
    final List<SignedBeaconBlock> response = requestBlocks(peer);
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldSendEmptyResponseWhenNoBlocksAreAvailable() throws Exception {
    final Eth2Peer peer = createPeer();
    final List<SignedBeaconBlock> response = requestBlocks(peer);
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldRespondWithBlocksFromCanonicalChain() throws Exception {
    final Eth2Peer peer = createPeer();

    final SignedBlockAndState block1 = peerStorage.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = peerStorage.chainUpdater().advanceChain();
    peerStorage.chainUpdater().updateBestBlock(block2);

    final List<SignedBeaconBlock> response = requestBlocks(peer);
    assertThat(response).containsExactly(block1.getBlock(), block2.getBlock());
  }

  @Test
  public void requestBlocksByRangeAfterPeerDisconnectedImmediately() throws Exception {
    final Eth2Peer peer = createPeer();

    // Setup chain
    peerStorage.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = peerStorage.chainUpdater().advanceChain();
    peerStorage.chainUpdater().updateBestBlock(block2);

    peer.disconnectImmediately(Optional.empty(), false);
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer.requestBlocksByRange(
            UInt64.ONE, UInt64.valueOf(10), RpcResponseListener.from(blocks::add));

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(blocks).isEmpty();
  }

  @Test
  public void requestBlocksByRangeAfterPeerDisconnected() throws Exception {
    final Eth2Peer peer = createPeer();

    // Setup chain
    peerStorage.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = peerStorage.chainUpdater().advanceChain();
    peerStorage.chainUpdater().updateBestBlock(block2);

    Waiter.waitFor(peer.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS));
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer.requestBlocksByRange(
            UInt64.ONE, UInt64.valueOf(10), RpcResponseListener.from(blocks::add));

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(blocks).isEmpty();
  }

  @Test
  public void requestBlockBySlotAfterPeerDisconnectedImmediately() throws Exception {
    final Eth2Peer peer = createPeer();

    // Setup chain
    peerStorage.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = peerStorage.chainUpdater().advanceChain();
    peerStorage.chainUpdater().updateBestBlock(block2);

    peer.disconnectImmediately(Optional.empty(), false);
    final SafeFuture<Optional<SignedBeaconBlock>> res = peer.requestBlockBySlot(UInt64.ONE);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
  }

  @Test
  public void requestBlockByRootAfterPeerDisconnected() throws Exception {
    final Eth2Peer peer = createPeer();

    // Setup chain
    peerStorage.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = peerStorage.chainUpdater().advanceChain();
    peerStorage.chainUpdater().updateBestBlock(block2);

    Waiter.waitFor(peer.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS));
    final SafeFuture<Optional<SignedBeaconBlock>> res = peer.requestBlockBySlot(UInt64.ONE);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
  }

  @Test
  void requestBlockBySlot_shouldReturnEmptyWhenBlockIsNotKnown() throws Exception {
    final Eth2Peer peer = createPeer();

    final Optional<SignedBeaconBlock> result =
        waitFor(peer.requestBlockBySlot(UInt64.valueOf(49382982)));
    assertThat(result).isEmpty();
  }

  @ParameterizedTest(name = "enableAltairLocally={0}, enableAltairRemotely={1}")
  @MethodSource("altairVersioningOptions")
  public void requestBlockBySlot_withDisparateVersionsEnabled_requestPhase0Blocks(
      final boolean enableAltairLocally, final boolean enableAltairRemotely) throws Exception {
    final Eth2Peer peer = createPeer(enableAltairLocally, enableAltairRemotely);

    // Setup chain
    final SignedBlockAndState block1 = peerStorage.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = peerStorage.chainUpdater().advanceChain();
    peerStorage.chainUpdater().updateBestBlock(block2);
    assertThat(block1.getBlock().getMessage().getBody()).isInstanceOf(BeaconBlockBodyPhase0.class);
    assertThat(block1.getBlock().getMessage().getBody()).isInstanceOf(BeaconBlockBodyPhase0.class);

    final List<SignedBeaconBlock> response = requestBlocks(peer);
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
        peer.requestBlocksByRange(
            block1.getSlot(), UInt64.valueOf(2), RpcResponseListener.from(blocks::add));

    waitFor(() -> assertThat(res).isDone());
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);

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
          .hasMessageContaining("Must request blocks with compatible fork.");
    } else {
      // Remote only supports v1, we should get a v1 response back and error out trying to
      // decode these blocks with a phase0 decoder
      assertThat(res).isCompletedExceptionally();
      assertThatThrownBy(res::get).hasCauseInstanceOf(DeserializationFailedException.class);
    }
  }

  @Test
  public void testRequestingBlocksByRangeForEip4844() {
    final Eth2Peer peer = createPeer(TestSpecFactory.createMinimalEip4844());
    // Create blocks
    final SignedBlockAndState block1 = peerStorage.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = peerStorage.chainUpdater().advanceChain();

    peerStorage.chainUpdater().updateBestBlock(block2);

    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer.requestBlocksByRange(
            block1.getSlot(), UInt64.valueOf(2), RpcResponseListener.from(blocks::add));

    waitFor(() -> assertThat(res).isDone());
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);

    assertThat(res).isCompleted();
    assertThat(blocks)
        .containsExactly(block1.getBlock(), block2.getBlock())
        .allSatisfy(
            block ->
                assertThat(block.getMessage().getBody())
                    .isInstanceOf(BeaconBlockBodyEip4844.class));
  }

  public static Stream<Arguments> altairVersioningOptions() {
    return Stream.of(
        Arguments.of(true, true),
        Arguments.of(false, true),
        Arguments.of(true, false),
        Arguments.of(false, false));
  }

  private List<SignedBeaconBlock> requestBlocks(final Eth2Peer peer)
      throws InterruptedException, ExecutionException, TimeoutException {
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    waitFor(
        peer.requestBlocksByRange(
            UInt64.ONE, UInt64.valueOf(10), RpcResponseListener.from(blocks::add)));
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    return blocks;
  }
}
