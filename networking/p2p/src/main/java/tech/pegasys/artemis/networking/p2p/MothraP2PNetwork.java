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
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.p2p.mothra;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.mothra.MothraHandler;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.alogger.ALogger;

/**
 * Hobbits Ethereum Wire Protocol implementation.
 *
 * <p>This P2P implementation uses clear messages relying on the hobbits wire format.
 */
public final class MothraP2PNetwork implements P2PNetwork {
  private static final ALogger LOG = new ALogger(MothraP2PNetwork.class.getName());
  private static final ALogger STDOUT = new ALogger("stdout");
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final EventBus eventBus;
  private final ChainStorageClient store;
  private final int port;
  private final String networkInterface;
  private final boolean isBootnode;
  private final String userAgent = "Artemis SNAPSHOT";
  private GossipProtocol gossipProtocol;
  private String[] args;
  private final MothraHandler handler;

  /**
   * Default constructor
   *
   * @param eventBus the event bus of the instance
   * @param port the port to bind to
   * @param networkInterface the network interface to bind to
   */
  public MothraP2PNetwork(
      EventBus eventBus,
      ChainStorageClient store,
      int port,
      String networkInterface,
      boolean isBootnode) {
    this.eventBus = eventBus;
    this.store = store;
    this.port = port;
    this.networkInterface = networkInterface;
    this.isBootnode = isBootnode;
    this.gossipProtocol = GossipProtocol.GOSSIPSUB;
    eventBus.register(this);
    this.handler = new MothraHandler(this.eventBus);
    mothra.Init();
    mothra.ReceivedMessage = this.handler::handleMessage;
    this.args = processArgs();
  }

  private String[] processArgs() {
    String sargs = "./artemis ";
    if (!isBootnode) {
      sargs +=
          "--boot-nodes -IW4QKaKpM5ljLpEEuFjcmoqFVYpY2PVGigNX3vFWzJzfjESWmltcztnrgKP8hLHKShBZTd2lIfjpwCiZCtK8GjPQq4DgmlwhH8AAAGDdGNwgiMog3VkcIIjKIlzZWNwMjU2azGhA7mA0yD2yMhLDZ2cHtQCe-2xhLrBmcCM2Eg9jYWDFqk5 --listen-address "
              + this.networkInterface
              + " --port "
              + String.valueOf(this.port)
              + " --datadir /tmp/.artemis";
    }
    return sargs.split(" ");
  }

  @Override
  public void run() {
    if (started.compareAndSet(false, true)) {
      mothra.Start(args);
    }
  }

  @Override
  public Collection<?> getPeers() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<?> getHandlers() {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<?> connect(String peer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void subscribe(String event) {
    // TODO
    if (!started.get()) {}
  }

  @Override
  public void stop() {
    if (started.compareAndSet(true, false)) {}
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
