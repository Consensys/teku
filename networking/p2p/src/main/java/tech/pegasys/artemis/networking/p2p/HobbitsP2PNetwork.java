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

package tech.pegasys.artemis.networking.p2p;

import com.google.common.eventbus.EventBus;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.consensys.cava.concurrent.AsyncCompletion;
import net.consensys.cava.concurrent.CompletableAsyncCompletion;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;

/**
 * Hobbits Ethereum Wire Protocol implementation.
 *
 * <p>This P2P implementation uses clear messages relying on the hobbits wire format.
 */
public final class HobbitsP2PNetwork implements P2PNetwork {

  private final AtomicBoolean started = new AtomicBoolean(false);
  private final EventBus eventBus;
  private final Vertx vertx;
  private final int port;
  private final int advertisedPort;
  private final String networkInterface;
  private NetServer server;
  private NetClient client;

  /**
   * Default constructor
   *
   * @param eventBus the event bus of the instance
   * @param vertx the vertx instance to rely to build network elements
   * @param port the port to bind to
   * @param advertisedPort the port to advertise on
   * @param networkInterface the network interface to bind to
   */
  public HobbitsP2PNetwork(
      EventBus eventBus, Vertx vertx, int port, int advertisedPort, String networkInterface) {
    this.eventBus = eventBus;
    this.vertx = vertx;
    this.port = port;
    this.advertisedPort = advertisedPort;
    this.networkInterface = networkInterface;
  }

  @Override
  public void run() {
    if (started.compareAndSet(false, true)) {
      client = vertx.createNetClient(new NetClientOptions().setTcpKeepAlive(true));
      server =
          vertx
              .createNetServer(
                  new NetServerOptions()
                      .setPort(port)
                      .setHost(networkInterface)
                      .setTcpKeepAlive(true))
              .connectHandler(this::receiveMessage);
    }
  }

  private void receiveMessage(NetSocket netSocket) {
    netSocket.write(Buffer.buffer("there and back again")); // TODO
  }

  @Override
  public Collection<?> getPeers() {
    return null;
  }

  @Override
  public CompletableFuture<?> connect(String peer) {
    return null;
  }

  @Override
  public void subscribe(String event) {}

  @Override
  public void stop() {
    if (started.compareAndSet(true, false)) {
      try {
        CompletableAsyncCompletion completed = AsyncCompletion.incomplete();
        server.close(
            res -> {
              if (res.failed()) {
                completed.completeExceptionally(res.cause());
              } else {
                completed.complete();
              }
            });
        completed.join(10, TimeUnit.SECONDS);
      } catch (InterruptedException | TimeoutException e) {
        throw new RuntimeException(e);
      } finally {
        client.close();
      }
    }
  }

  @Override
  public boolean isListening() {
    return started.get();
  }

  @Override
  public void close() throws IOException {
    stop();
  }
}
