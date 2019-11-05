package tech.pegasys.artemis.networking.p2p.jvmlibp2p;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.util.Waiter.waitFor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.network.p2p.jvmlibp2p.NetworkFactory;
import tech.pegasys.artemis.networking.p2p.JvmLibP2PNetwork;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.RpcMethod;

public class GoodbyeIntegrationTest {
  private final NetworkFactory networkFactory = new NetworkFactory();
  private JvmLibP2PNetwork network1;
  private JvmLibP2PNetwork network2;
  private Peer peer1;

  @BeforeEach
  public void setUp() throws Exception {
    network1 = networkFactory.startNetwork();
    network2 = networkFactory.startNetwork(network1);
    peer1 = network2.getPeerManager().getAvailablePeer(network1.getPeerId()).orElseThrow();
  }

  @AfterEach
  public void tearDown() {
    networkFactory.stopAll();
  }

  @Test
  public void shouldCloseConnectionAfterGoodbyeReceived() throws Exception {
    final Peer peer2 =
        network1.getPeerManager().getAvailablePeer(network2.getPeerId()).orElseThrow();
    final GoodbyeMessage response =
        waitFor(
            peer1.send(
                RpcMethod.GOODBYE, new GoodbyeMessage(GoodbyeMessage.REASON_CLIENT_SHUT_DOWN)));
    assertThat(response).isNull();
    waitFor(() -> assertThat(peer1.isConnected()).isFalse());
    waitFor(() -> assertThat(peer2.isConnected()).isFalse());
  }
}
