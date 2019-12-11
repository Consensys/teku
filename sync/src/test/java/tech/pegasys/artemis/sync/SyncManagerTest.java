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

package tech.pegasys.artemis.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.eth2.Eth2Network;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.Waiter;

public class SyncManagerTest {

  private ChainStorageClient storageClient;
  private Eth2Network network;
  private BlockImporter blockImporter;
  private SyncManager syncManager;

  @BeforeEach
  public void setUp() throws Exception {
    storageClient = mock(ChainStorageClient.class);
    network = mock(Eth2Network.class);
    blockImporter = mock(BlockImporter.class);
    syncManager = spy(new SyncManager(network, storageClient, blockImporter));
  }

  @Test
  void sync_noPeers() throws Exception {
    when(storageClient.getFinalizedEpoch()).thenReturn(UnsignedLong.ZERO);
    when(network.streamPeers()).thenReturn(Stream.empty());
    Waiter.waitFor(() -> syncManager.sync());
    verify(syncManager, never()).requestSyncBlocks(any(), any());
  }

  @Test
  void sync_correct() {
    UnsignedLong finalizedEpoch = UnsignedLong.valueOf(3);
    when(storageClient.getFinalizedEpoch())
        .thenReturn(UnsignedLong.ZERO)
        .thenReturn(finalizedEpoch);
    Eth2Peer peer = mock(Eth2Peer.class);
    Eth2Peer.StatusData data = mock(Eth2Peer.StatusData.class);
    when(data.getFinalizedEpoch()).thenReturn(finalizedEpoch);
    when(peer.getStatus()).thenReturn(data);
    when(network.streamPeers()).thenReturn(Stream.of(peer));
    doReturn(CompletableFuture.completedFuture(null))
        .when(syncManager)
        .requestSyncBlocks(any(), any());

    Waiter.waitFor(() -> syncManager.sync());
    verify(syncManager, times(1)).requestSyncBlocks(eq(peer), any());
  }

  @Test
  void sync_badAdvertisedFinalizedEpoch() {
    UnsignedLong finalizedEpoch = UnsignedLong.valueOf(3);
    when(storageClient.getFinalizedEpoch())
        .thenReturn(UnsignedLong.ZERO)
        .thenReturn(finalizedEpoch.minus(UnsignedLong.ONE));
    Eth2Peer peer = mock(Eth2Peer.class);
    Eth2Peer.StatusData data = mock(Eth2Peer.StatusData.class);
    when(data.getFinalizedEpoch()).thenReturn(finalizedEpoch);
    when(peer.getStatus()).thenReturn(data);
    when(network.streamPeers()).thenReturn(Stream.of(peer));
    doReturn(CompletableFuture.completedFuture(null))
        .when(syncManager)
        .requestSyncBlocks(any(), any());

    Waiter.waitFor(() -> syncManager.sync());
    verify(syncManager, times(1)).disconnectFromPeerAndRunSyncAgain(eq(peer));
  }

  @Test
  void sync_badBlock() {
    // currently unsure how to do this since we're mocking Eth2Peers and Eth2Network
    // i.e. how is anyone supposed to call the ResponseStream.ResponseListener when
    // all the networking code we have is mocks
  }
}
