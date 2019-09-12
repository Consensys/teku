package tech.pegasys.artemis.networking.p2p;

import com.google.common.eventbus.EventBus;
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
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.networking.p2p.api.P2PNetwork;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.GossipMessageHandler;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.JvmLibP2PConfig;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.LibP2PPeerManager;
import tech.pegasys.artemis.util.alogger.ALogger;

public class JvmLibP2PNetwork implements P2PNetwork {
  private static final ALogger LOG = new ALogger(JvmLibP2PNetwork.class.getName());
  private static final ALogger STDOUT = new ALogger("stdout");

  private final PrivKey privKey;
  private final JvmLibP2PConfig config;
  private final Host host;
  private final ScheduledExecutorService scheduler;
  private final LibP2PPeerManager peerManager;
  private long activePeerReconnectTimeoutSec = 5;

  public JvmLibP2PNetwork(final JvmLibP2PConfig config, final EventBus eventBus) {
    this.privKey =
        config
            .getPrivateKey()
            .orElseGet(() -> KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1());
    this.config = config;
    scheduler = Executors.newSingleThreadScheduledExecutor();
    Gossip gossip = new Gossip(); // TODO gossip params
    //        RpcMessageCodecFactory rpcCodecFactory = SSZMessageCodec.createFactory(sszSerializer);
    GossipMessageHandler.init(gossip, privKey, eventBus);
    //     privKey);
    //        WireApiSub wireApiSub = logEthPubsub ? new DebugWireApiSub(gossipSub, spec) :
    // gossipSub;
    //        peerManager = new Libp2pPeerManager(
    //            spec, fork, schedulers, headStream, wireApiSub, rpcCodecFactory,
    // wireApiSyncServer);
    peerManager = new LibP2PPeerManager();

    host =
        BuildersJKt.hostJ(
            b -> {
              b.getIdentity().setFactory(() -> privKey);
              b.getTransports().add(TcpTransport::new);
              b.getSecureChannels().add(SecIoSecureChannel::new);
              b.getMuxers().add(MplexStreamMuxer::new);
              config
                  .getListenPort()
                  .ifPresent(port -> b.getNetwork().listen("/ip4/0.0.0.0/tcp/" + port));

              IdentifyOuterClass.Identify identifyMsg =
                  IdentifyOuterClass.Identify.newBuilder()
                      .setProtocolVersion("ipfs/0.1.0")
                      .setAgentVersion("jvm/0.1")
                      .setPublicKey(ByteArrayExtKt.toProtobuf(privKey.publicKey().bytes()))
                      .addListenAddrs(
                          ByteArrayExtKt.toProtobuf(
                              new Multiaddr("/ip4/127.0.0.1/tcp/45555").getBytes()))
                      .setObservedAddr(
                          ByteArrayExtKt.toProtobuf(
                              new Multiaddr("/ip4/127.0.0.1/tcp/45555").getBytes()))
                      .addProtocols("/ipfs/id/1.0.0")
                      .addProtocols("/ipfs/id/push/1.0.0")
                      .addProtocols("/ipfs/ping/1.0.0")
                      .addProtocols("/libp2p/circuit/relay/0.1.0")
                      .addProtocols("/meshsub/1.0.0")
                      .addProtocols("/floodsub/1.0.0")
                      .build();

              b.getProtocols().add(new Ping());
              b.getProtocols().add(new Identify(identifyMsg));
              b.getProtocols().add(gossip);
              //              b.getProtocols().addAll(peerManager.rpcMethods.all());

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
  public void run() {
    STDOUT.log(Level.INFO, "Starting libp2p network...");
    host.start()
        .thenApply(
            i -> {
              STDOUT.log(
                  Level.INFO,
                  "Listening for connections on port "
                      + config.getListenPort().map(Object::toString).orElse("<not listening>")
                      + " with peerId "
                      + PeerId.fromPubKey(privKey.publicKey()).toBase58());
              return null;
            });

    for (String peer : config.getPeers()) {
      connect(peer);
    }
  }

  @Override
  public Collection<?> getPeers() {
    return null;
  }

  @Override
  public Collection<?> getHandlers() {
    return null;
  }

  @Override
  public CompletableFuture<?> connect(final String peer) {
    STDOUT.log(Level.INFO, "Connecting to " + peer);
    return host.getNetwork()
        .connect(new Multiaddr(peer))
        .whenComplete(
            (conn, t) -> {
              if (t != null) {
                STDOUT.log(
                    Level.INFO, "Connection to " + peer + " failed. Will retry shortly : " + t);
                scheduler.schedule(
                    () -> connect(peer), activePeerReconnectTimeoutSec, TimeUnit.SECONDS);
              } else {
                conn.closeFuture()
                    .thenAccept(
                        ignore -> {
                          LOG.log(
                              Level.INFO, "Connection to " + peer + " closed. Will retry shortly");
                          scheduler.schedule(
                              () -> connect(peer), activePeerReconnectTimeoutSec, TimeUnit.SECONDS);
                        });
              }
            });
  }

  @Override
  public void subscribe(final String event) {}

  @Override
  public void stop() {}

  @Override
  public boolean isListening() {
    return false;
  }

  @Override
  public void close() throws IOException {
    try {
      host.stop().get(10, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new IOException(e);
    }
  }
}
