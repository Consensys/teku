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

package tech.pegasys.teku.beaconrestapi.v1.node;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.node.Identity;
import tech.pegasys.teku.api.response.v1.node.IdentityResponse;
import tech.pegasys.teku.api.schema.Metadata;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetIdentity;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.MetadataMessage;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.util.config.Constants;

public class GetIdentityIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @Test
  public void shouldReturnNetworkIdentity() throws Exception {
    startRestAPIAtGenesis();
    String enr = "enr";
    String address = "address";
    String discoveryAddress = "discoveryaddress";
    final MockNodeId node1 = new MockNodeId(0);
    final UInt64 seqnr = dataStructureUtil.randomUInt64();
    final Bitvector attnets = dataStructureUtil.randomBitvector(Constants.ATTESTATION_SUBNET_COUNT);
    final MetadataMessage metadataMessage = new MetadataMessage(seqnr, attnets);

    when(eth2Network.getNodeId()).thenReturn(node1);
    when(eth2Network.getEnr()).thenReturn(Optional.of(enr));
    when(eth2Network.getNodeAddress()).thenReturn(address);
    when(eth2Network.getDiscoveryAddress()).thenReturn(Optional.of(discoveryAddress));
    when(eth2Network.getMetadata()).thenReturn(metadataMessage);

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
