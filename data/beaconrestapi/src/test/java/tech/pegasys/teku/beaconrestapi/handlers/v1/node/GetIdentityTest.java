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

package tech.pegasys.teku.beaconrestapi.handlers.v1.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.node.IdentityResponse;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;

public class GetIdentityTest extends AbstractBeaconHandlerTest {

  @Test
  public void shouldReturnExpectedObjectType() throws Exception {
    final MetadataMessage defaultMetadata =
        spec.getGenesisSchemaDefinitions().getMetadataMessageSchema().createDefault();

    GetIdentity handler = new GetIdentity(network, jsonProvider);
    NodeId nodeid = mock(NodeId.class);

    when(eth2P2PNetwork.getMetadata()).thenReturn(defaultMetadata);
    when(eth2P2PNetwork.getNodeId()).thenReturn(nodeid);
    when(nodeid.toBase58()).thenReturn("aeiou");
    when(eth2P2PNetwork.getNodeAddress()).thenReturn("address");

    handler.handle(context);
    verifyCacheStatus(CACHE_NONE);

    IdentityResponse response = getResponseObject(IdentityResponse.class);
    assertThat(response.data.peerId).isEqualTo("aeiou");
    assertThat(response.data.p2pAddresses.get(0)).isEqualTo("address");
  }
}
