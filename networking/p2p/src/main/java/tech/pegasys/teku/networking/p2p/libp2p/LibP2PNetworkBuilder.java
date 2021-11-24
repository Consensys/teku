package tech.pegasys.teku.networking.p2p.libp2p;

import static tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetwork.REMOTE_OPEN_STREAMS_RATE_LIMIT;
import static tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetwork.REMOTE_PARALLEL_OPEN_STREAMS_COUNT_LIMIT;

import identify.pb.IdentifyOuterClass;
import io.libp2p.core.Host;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.core.dsl.Builder.Defaults;
import io.libp2p.core.dsl.BuilderJKt;
import io.libp2p.core.multiformats.Multiaddr;
import io.libp2p.core.multistream.ProtocolBinding;
import io.libp2p.core.mux.StreamMuxerProtocol;
import io.libp2p.etc.types.ByteArrayExtKt;
import io.libp2p.protocol.Identify;
import io.libp2p.protocol.Ping;
import io.libp2p.security.noise.NoiseXXSecureChannel;
import io.libp2p.transport.tcp.TcpTransport;
import io.netty.handler.logging.LogLevel;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessageFactory;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetwork.PrivateKeyProvider;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipTopicFilter;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.LibP2PGossipNetwork;
import tech.pegasys.teku.networking.p2p.libp2p.rpc.RpcHandler;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerHandler;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;

public class LibP2PNetworkBuilder {

  public static LibP2PNetworkBuilder create() {
    return new LibP2PNetworkBuilder();
  }

  protected AsyncRunner asyncRunner;
  protected NetworkConfig config;
  protected PrivateKeyProvider privateKeyProvider;
  protected ReputationManager reputationManager;
  protected MetricsSystem metricsSystem;
  protected List<RpcMethod<?, ?, ?>> rpcMethods;
  protected List<PeerHandler> peerHandlers;
  protected PreparedGossipMessageFactory preparedGossipMessageFactory;
  protected GossipTopicFilter gossipTopicFilter;

  protected LibP2PGossipNetwork gossipNetwork;
  protected PeerManager peerManager;

  protected Defaults hostBuilderDefaults = Defaults.None;
  protected Host host;

  protected LibP2PNetworkBuilder() {
  }

  public P2PNetwork<Peer> build() {
    if (gossipNetwork == null) {
      // Setup gossip
      gossipNetwork =
          LibP2PGossipNetwork.create(
              metricsSystem,
              config.getGossipConfig(),
              preparedGossipMessageFactory,
              gossipTopicFilter,
              config.getWireLogsConfig().isLogWireGossip());
    }

    // Setup rpc methods
    final List<RpcHandler<?, ?, ?>> rpcHandlers =
        rpcMethods.stream().map(m -> new RpcHandler<>(asyncRunner, m)).collect(Collectors.toList());

    if (peerManager == null) {
      // Setup peers
      peerManager =
          new PeerManager(
              metricsSystem,
              reputationManager,
              peerHandlers,
              rpcHandlers,
              (peerId) -> gossipNetwork.getGossip().getGossipScore(peerId));
    }

    PrivKey privKey = privateKeyProvider.get();
    NodeId nodeId = new LibP2PNodeId(PeerId.fromPubKey(privKey.publicKey()));

    Multiaddr advertisedAddr =
        MultiaddrUtil.fromInetSocketAddress(
            new InetSocketAddress(config.getAdvertisedIp(), config.getAdvertisedPort()), nodeId);
    final Multiaddr listenAddr =
        MultiaddrUtil.fromInetSocketAddress(
            new InetSocketAddress(config.getNetworkInterface(), config.getListenPort()));

    if (host == null) {
      host =
          BuilderJKt.hostJ(
              hostBuilderDefaults,
              b -> {
                b.getIdentity().setFactory(() -> privKey);
                b.getTransports().add(TcpTransport::new);
                b.getSecureChannels().add(NoiseXXSecureChannel::new);
                b.getMuxers().add(StreamMuxerProtocol.getMplex());

                b.getNetwork().listen(listenAddr.toString());

                b.getProtocols().addAll(getDefaultProtocols(privKey.publicKey(), advertisedAddr));
                b.getProtocols().addAll(rpcHandlers);

                if (config.getWireLogsConfig().isLogWireCipher()) {
                  b.getDebug().getBeforeSecureHandler().addLogger(LogLevel.DEBUG, "wire.ciphered");
                }
                Firewall firewall = new Firewall(Duration.ofSeconds(30));
                b.getDebug().getBeforeSecureHandler().addNettyHandler(firewall);

                if (config.getWireLogsConfig().isLogWirePlain()) {
                  b.getDebug().getAfterSecureHandler().addLogger(LogLevel.DEBUG, "wire.plain");
                }
                if (config.getWireLogsConfig().isLogWireMuxFrames()) {
                  b.getDebug().getMuxFramesHandler().addLogger(LogLevel.DEBUG, "wire.mux");
                }

                b.getConnectionHandlers().add(peerManager);

                MplexFirewall mplexFirewall =
                    new MplexFirewall(
                        REMOTE_OPEN_STREAMS_RATE_LIMIT, REMOTE_PARALLEL_OPEN_STREAMS_COUNT_LIMIT);
                b.getDebug().getMuxFramesHandler().addHandler(mplexFirewall);
              });
    }

    return new LibP2PNetwork(privKey, nodeId, host, peerManager, advertisedAddr, gossipNetwork,
        config.getListenPort());
  }

  protected List<ProtocolBinding<?>> getDefaultProtocols(PubKey nodePubKey, Multiaddr advertisedAddr) {
    final Ping ping = new Ping();
    IdentifyOuterClass.Identify identifyMsg =
        IdentifyOuterClass.Identify.newBuilder()
            .setProtocolVersion("ipfs/0.1.0")
            .setAgentVersion(VersionProvider.CLIENT_IDENTITY + "/" + VersionProvider.VERSION)
            .setPublicKey(ByteArrayExtKt.toProtobuf(nodePubKey.bytes()))
            .addListenAddrs(ByteArrayExtKt.toProtobuf(advertisedAddr.getBytes()))
            .setObservedAddr(ByteArrayExtKt.toProtobuf(advertisedAddr.getBytes()))
            .addAllProtocols(ping.getProtocolDescriptor().getAnnounceProtocols())
            .addAllProtocols(
                gossipNetwork.getGossip().getProtocolDescriptor().getAnnounceProtocols())
            .build();
    return List.of(ping, new Identify(identifyMsg), gossipNetwork.getGossip());
  }

  public LibP2PNetworkBuilder asyncRunner(AsyncRunner asyncRunner) {
    this.asyncRunner = asyncRunner;
    return this;
  }

  public LibP2PNetworkBuilder config(NetworkConfig config) {
    this.config = config;
    return this;
  }

  public LibP2PNetworkBuilder privateKeyProvider(
      PrivateKeyProvider privateKeyProvider) {
    this.privateKeyProvider = privateKeyProvider;
    return this;
  }

  public LibP2PNetworkBuilder reputationManager(
      ReputationManager reputationManager) {
    this.reputationManager = reputationManager;
    return this;
  }

  public LibP2PNetworkBuilder metricsSystem(MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    return this;
  }

  public LibP2PNetworkBuilder rpcMethods(
      List<RpcMethod<?, ?, ?>> rpcMethods) {
    this.rpcMethods = rpcMethods;
    return this;
  }

  public LibP2PNetworkBuilder peerHandlers(
      List<PeerHandler> peerHandlers) {
    this.peerHandlers = peerHandlers;
    return this;
  }

  public LibP2PNetworkBuilder preparedGossipMessageFactory(
      PreparedGossipMessageFactory preparedGossipMessageFactory) {
    this.preparedGossipMessageFactory = preparedGossipMessageFactory;
    return this;
  }

  public LibP2PNetworkBuilder gossipTopicFilter(
      GossipTopicFilter gossipTopicFilter) {
    this.gossipTopicFilter = gossipTopicFilter;
    return this;
  }

  public LibP2PNetworkBuilder gossipNetwork(
      LibP2PGossipNetwork gossipNetwork) {
    this.gossipNetwork = gossipNetwork;
    return this;
  }

  public LibP2PNetworkBuilder peerManager(PeerManager peerManager) {
    this.peerManager = peerManager;
    return this;
  }

  public LibP2PNetworkBuilder hostBuilderDefaults(Defaults hostBuilderDefaults) {
    this.hostBuilderDefaults = hostBuilderDefaults;
    return this;
  }

  public LibP2PNetworkBuilder host(Host host) {
    this.host = host;
    return this;
  }
}
