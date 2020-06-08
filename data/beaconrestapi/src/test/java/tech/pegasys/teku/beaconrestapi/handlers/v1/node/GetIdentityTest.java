package tech.pegasys.teku.beaconrestapi.handlers.v1.node;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.response.v1.node.IdentityResponse;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.provider.JsonProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;

public class GetIdentityTest {
  private final JsonProvider jsonProvider = new JsonProvider();
  private final Context context = mock(Context.class);
  @SuppressWarnings("unchecked")
  private final P2PNetwork<Peer> p2pNetwork = mock(P2PNetwork.class);
  private final NetworkDataProvider network = new NetworkDataProvider(p2pNetwork);

  private final ArgumentCaptor<String> stringArgs = ArgumentCaptor.forClass(String.class);

  @Test
  public void shouldReturnExpectedObjectType() throws Exception {
    GetIdentity handler = new GetIdentity(network, jsonProvider);
    NodeId nodeid = mock(NodeId.class);

    when(p2pNetwork.getNodeId()).thenReturn(nodeid);
    when(nodeid.toBase58()).thenReturn("aeiou");
    when(p2pNetwork.getNodeAddress()).thenReturn("address");

    handler.handle(context);
    verify(context).result(stringArgs.capture());
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    String val = stringArgs.getValue();
    assertThat(val).isNotNull();
    IdentityResponse response = jsonProvider.jsonToObject(val, IdentityResponse.class);
    assertThat(response.data.peerId).isEqualTo("aeiou");
    assertThat(response.data.p2pAddresses.get(0)).isEqualTo("address");
  }
}
