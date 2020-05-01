/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.networking.eth2.rpc.core;

import static com.google.common.base.Preconditions.checkArgument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.peers.PeerLookup;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods.MetadataMessageFactory;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.artemis.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.artemis.networking.p2p.mock.MockNodeId;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.artemis.networking.p2p.rpc.RpcStream;
import tech.pegasys.artemis.storage.client.CombinedChainDataClient;
import tech.pegasys.artemis.storage.client.RecentChainData;
import tech.pegasys.artemis.util.Waiter;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.async.StubAsyncRunner;
import tech.pegasys.artemis.util.iostreams.MockInputStream;

abstract class AbstractRequestHandlerTest<T extends RpcRequestHandler> {
  private static final Logger LOG = LogManager.getLogger();

  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  protected final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  protected final PeerLookup peerLookup = mock(PeerLookup.class);
  protected final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  protected final RecentChainData recentChainData = mock(RecentChainData.class);

  protected BeaconChainMethods beaconChainMethods;

  protected final NodeId nodeId = new MockNodeId();
  protected final RpcStream rpcStream = mock(RpcStream.class);
  protected final Eth2Peer peer = mock(Eth2Peer.class);
  protected T reqHandler;

  private Thread inputHandlerThread;
  private final AtomicBoolean inputHandlerDone = new AtomicBoolean(false);
  protected final MockInputStream inputStream = new MockInputStream();

  @BeforeEach
  public void setup() {
    beaconChainMethods =
        BeaconChainMethods.create(
            asyncRunner,
            peerLookup,
            combinedChainDataClient,
            recentChainData,
            new NoOpMetricsSystem(),
            new StatusMessageFactory(recentChainData),
            new MetadataMessageFactory(),
            getRpcEncoding());

    reqHandler = createRequestHandler(beaconChainMethods);

    lenient().when(rpcStream.close()).thenReturn(SafeFuture.COMPLETE);
    lenient().when(rpcStream.closeWriteStream()).thenReturn(SafeFuture.COMPLETE);
    lenient().when(rpcStream.writeBytes(any())).thenReturn(SafeFuture.COMPLETE);
    lenient().when(peerLookup.getConnectedPeer(nodeId)).thenReturn(peer);

    // Setup thread to process input
    startProcessingInput();
  }

  protected void startProcessingInput() {
    inputHandlerThread =
        new Thread(
            () -> {
              try {
                reqHandler.processInput(nodeId, rpcStream, inputStream);
              } catch (Throwable t) {
                LOG.warn("Caught error while processing input: ", t);
              } finally {
                inputHandlerDone.set(true);
              }
            });
    inputHandlerThread.start();
  }

  @AfterEach
  public void teardown() {
    inputStream.close();
    if (inputHandlerThread != null) {
      inputHandlerThread.interrupt();
    }
    Waiter.waitFor(() -> assertThat(inputHandlerDone).isTrue());
  }

  protected abstract T createRequestHandler(final BeaconChainMethods beaconChainMethods);

  protected abstract RpcEncoding getRpcEncoding();

  protected void deliverBytes(final Bytes bytes) throws IOException {
    deliverBytes(bytes, bytes.size());
  }

  protected void deliverBytes(final Bytes bytes, final int waitUntilBytesConsumed)
      throws IOException {
    checkArgument(
        waitUntilBytesConsumed <= bytes.size(), "Cannot wait for more bytes than those supplied.");
    inputStream.deliverBytes(bytes);
    final int maxRemainingBytes = bytes.size() - waitUntilBytesConsumed;
    Waiter.waitFor(
        () ->
            assertThat(inputStream.countUnconsumedBytes()).isLessThanOrEqualTo(maxRemainingBytes));
  }
}
