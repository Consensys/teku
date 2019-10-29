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
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import identify.pb.IdentifyOuterClass;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.dsl.BuildersJKt;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.etc.types.ByteArrayExtKt;
import io.libp2p.mux.mplex.MplexStreamMuxer;
import io.libp2p.protocol.Identify;
import io.libp2p.protocol.Ping;
import io.libp2p.pubsub.gossip.Gossip;
import io.libp2p.security.secio.SecIoSecureChannel;
import io.libp2p.transport.tcp.TcpTransport;
import io.netty.handler.logging.LogLevel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.Config;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.PeerManager;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.gossip.GossipMessageHandler;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.cli.VersionProvider;
import tech.pegasys.pantheon.metrics.MetricsSystem;

public class JvmLibP2PNetwork implements P2PNetwork {
  private static final ALogger STDOUT = new ALogger("stdout");

  private final PrivKey privKey;
  private final Config config;

  private final Host host;
  private final ScheduledExecutorService scheduler;
  private final PeerManager peerManager;
  private final Multiaddr advertisedAddr;

  public JvmLibP2PNetwork(
      final Config config,
      final EventBus eventBus,
      final ChainStorageClient chainStorageClient,
      final MetricsSystem metricsSystem) {
    this.privKey =
        config
            .getPrivateKey()
            .orElseGet(() -> KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1());
    this.config = config;
    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setDaemon(true).setNameFormat("libp2p-%d").build());
    Gossip gossip = new Gossip();
    GossipMessageHandler.init(gossip, privKey, eventBus);
    peerManager = new PeerManager(scheduler, chainStorageClient, metricsSystem);
    advertisedAddr = new Multiaddr("/ip4/127.0.0.1/tcp/" + config.getAdvertisedPort());

    host =
        BuildersJKt.hostJ(
            b -> {
              b.getIdentity().setFactory(() -> privKey);
              b.getTransports().add(TcpTransport::new);
              b.getSecureChannels().add(SecIoSecureChannel::new);
              b.getMuxers().add(MplexStreamMuxer::new);
              b.getNetwork()
                  .listen(
                      "/ip4/" + config.getNetworkInterface() + "/tcp/" + config.getListenPort());

              final Ping ping = new Ping();
              IdentifyOuterClass.Identify identifyMsg =
                  IdentifyOuterClass.Identify.newBuilder()
                      .setProtocolVersion("ipfs/0.1.0")
                      .setAgentVersion(
                          VersionProvider.CLIENT_IDENTITY + "/" + VersionProvider.VERSION)
                      .setPublicKey(ByteArrayExtKt.toProtobuf(privKey.publicKey().bytes()))
                      .addListenAddrs(ByteArrayExtKt.toProtobuf(advertisedAddr.getBytes()))
                      .setObservedAddr(
                          ByteArrayExtKt.toProtobuf( // TODO: Report external IP?
                              advertisedAddr.getBytes()))
                      .addProtocols(ping.getAnnounce())
                      .addProtocols(gossip.getAnnounce())
                      .build();

              b.getProtocols().add(ping);
              b.getProtocols().add(new Identify(identifyMsg));
              b.getProtocols().add(gossip);
              b.getProtocols().addAll(peerManager.getRpcMessageHandlers());

              if (config.isLogWireCipher()) {
                b.getDebug().getBeforeSecureHandler().setLogger(LogLevel.DEBUG, "wire.ciphered");
              }
              if (config.isLogWirePlain()) {
                b.getDebug().getAfterSecureHandler().setLogger(LogLevel.DEBUG, "wire.plain");
              }
              if (config.isLogMuxFrames()) {
                b.getDebug().getMuxFramesHandler().setLogger(LogLevel.DEBUG, "wire.mux");
              }

              b.getConnectionHandlers().add(peerManager);
            });
  }

  @Override
  public CompletableFuture<?> start() {
    STDOUT.log(Level.INFO, "Starting libp2p network...");
    return host.start()
        .thenApply(
            i -> {
              STDOUT.log(
                  Level.INFO,
                  "Listening for connections on port "
                      + config.getListenPort()
                      + " with peerId "
                      + getPeerId());
              return null;
            })
        .thenRun(() -> config.getPeers().forEach(this::connect));
  }

  private String getPeerId() {
    return PeerId.fromPubKey(privKey.publicKey()).toBase58();
  }

  public String getPeerAddress() {
    return advertisedAddr + "/p2p/" + getPeerId();
  }

  public int getPeerCount() {
    return peerManager.getPeerCount();
  }

  @Override
  public CompletableFuture<?> connect(final String peer) {
    return peerManager.connect(new Multiaddr(peer), host.getNetwork());
  }

  @Override
  public void stop() {
    STDOUT.log(Level.DEBUG, "JvmLibP2PNetwork.stop()");
    host.stop();
    scheduler.shutdownNow();
  }
}
