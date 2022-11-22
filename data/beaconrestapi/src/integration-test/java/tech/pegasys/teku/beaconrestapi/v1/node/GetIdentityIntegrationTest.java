/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.beaconrestapi.v1.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.node.Identity;
import tech.pegasys.teku.api.response.v1.node.IdentityResponse;
import tech.pegasys.teku.api.schema.Metadata;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetIdentity;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetIdentityIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final String enr = "enr";
  private final String address = "address";
  private final String discoveryAddress = "discoveryaddress";
  private final MockNodeId node1 = new MockNodeId(0);
  private final UInt64 seqnr = dataStructureUtil.randomUInt64();

  @BeforeEach
  void setup() {
    when(eth2P2PNetwork.getNodeId()).thenReturn(node1);
    when(eth2P2PNetwork.getEnr()).thenReturn(Optional.of(enr));
    when(eth2P2PNetwork.getNodeAddress()).thenReturn(address);
    when(eth2P2PNetwork.getDiscoveryAddress()).thenReturn(Optional.of(discoveryAddress));
  }

  @Test
  public void shouldReturnNetworkIdentity() throws Exception {
    startRestAPIAtGenesis();
    final MetadataMessage metadataMessage =
        spec.getGenesisSchemaDefinitions()
            .getMetadataMessageSchema()
            .create(seqnr, List.of(1, 11, 15), Collections.emptyList());

    when(eth2P2PNetwork.getMetadata()).thenReturn(metadataMessage);

    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_OK);
    final IdentityResponse identityResponse =
        jsonProvider.jsonToObject(response.body().string(), IdentityResponse.class);
    assertThat(identityResponse.data)
        .isEqualTo(
            new Identity(
                node1.toBase58(),
                enr,
                List.of(address),
                List.of(discoveryAddress),
                new Metadata(metadataMessage)));
  }

  @Test
  public void shouldReturnNetworkIdentityAltair() throws Exception {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);

    final MetadataMessage metadataMessage =
        spec.getGenesisSchemaDefinitions()
            .getMetadataMessageSchema()
            .create(seqnr, List.of(1, 11, 15), List.of(0, 1, 2, 3));

    when(eth2P2PNetwork.getMetadata()).thenReturn(metadataMessage);

    final Response response = get();
    assertThat(response.code()).isEqualTo(SC_OK);
    final IdentityResponse identityResponse =
        jsonProvider.jsonToObject(response.body().string(), IdentityResponse.class);
    assertThat(identityResponse.data)
        .isEqualTo(
            new Identity(
                node1.toBase58(),
                enr,
                List.of(address),
                List.of(discoveryAddress),
                new Metadata(metadataMessage)));
  }

  private Response get() throws IOException {
    return getResponse(GetIdentity.ROUTE);
  }
}
