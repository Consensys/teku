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

package tech.pegasys.teku.beaconrestapi.handlers.v1.node;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_NODE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.string;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;
import static tech.pegasys.teku.infrastructure.restapi.endpoints.CacheLength.NO_CACHE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.response.v1.node.IdentityResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;

public class GetIdentity extends MigratingEndpointAdapter {
  public static final String ROUTE = "/eth/v1/node/identity";

  private static final SerializableTypeDefinition<MetadataMessage> METADATA_TYPE =
      SerializableTypeDefinition.object(MetadataMessage.class)
          .name("MetaData")
          .withField(
              "seq_number",
              UINT64_TYPE.withDescription(
                  "Uint64 starting at 0 used to version the node's metadata. "
                      + "If any other field in the local MetaData changes, the node MUST increment seq_number by 1."),
              MetadataMessage::getSeqNumber)
          .withField(
              "attnets",
              SszBitvectorSchema.create(Constants.ATTESTATION_SUBNET_COUNT)
                  .getJsonTypeDefinition()
                  .withDescription(
                      "Bitvector representing the node's persistent attestation subnet subscriptions."),
              MetadataMessage::getAttnets)
          .withOptionalField(
              "syncnets",
              SszBitvectorSchema.create(NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT)
                  .getJsonTypeDefinition()
                  .withDescription(
                      "Bitvector representing the node's persistent sync committee subnet subscriptions."),
              MetadataMessage::getOptionalSyncnets)
          .build();

  private static final SerializableTypeDefinition<IdentityData> IDENTITY_DATA_TYPE =
      SerializableTypeDefinition.object(IdentityData.class)
          .name("NetworkIdentity")
          .withField(
              "peer_id",
              string(
                  "Cryptographic hash of a peer’s public key. "
                      + "[Read more](https://docs.libp2p.io/concepts/peer-id/)",
                  "QmYyQSo1c1Ym7orWxLYvCrM2EmxFTANf8wXmmE7DWjhx5N"),
              IdentityData::getPeerId)
          .withOptionalField(
              "enr",
              string(
                  "Ethereum node record. [Read more](https://eips.ethereum.org/EIPS/eip-778)",
                  "enr:-IS4QHCYrYZbAKWCBRlAy5zzaDZXJBGkcnh4MHcBFZntXNFrdvJjX04jRzjzCBOonrkTfj499SZuOh8R33Ls8RRcy5wBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCdl8"),
              IdentityData::getEnr)
          .withField(
              "p2p_addresses",
              listOf(
                  string(
                      "Node's addresses on which eth2 rpc requests are served. "
                          + "[Read more](https://docs.libp2p.io/reference/glossary/#multiaddr)",
                      "/ip4/7.7.7.7/tcp/4242/p2p/QmYyQSo1c1Ym7orWxLYvCrM2EmxFTANf8wXmmE7DWjhx5N")),
              IdentityData::getListeningAddresses)
          .withField(
              "discovery_addresses",
              listOf(
                  string(
                      "Node's addresses on which is listening for discv5 requests. "
                          + "[Read more](https://docs.libp2p.io/reference/glossary/#multiaddr)",
                      "/ip4/7.7.7.7/udp/30303/p2p/QmYyQSo1c1Ym7orWxLYvCrM2EmxFTANf8wXmmE7DWjhx5N")),
              IdentityData::getDiscoveryAddresses)
          .withField("metadata", METADATA_TYPE, IdentityData::getMetadata)
          .build();

  private static final SerializableTypeDefinition<IdentityData> IDENTITY_RESPONSE_TYPE =
      SerializableTypeDefinition.object(IdentityData.class)
          .name("GetNetworkIdentityResponse")
          .withField("data", IDENTITY_DATA_TYPE, Function.identity())
          .build();
  private final NetworkDataProvider network;

  public GetIdentity(final DataProvider provider) {
    this(provider.getNetworkDataProvider());
  }

  GetIdentity(final NetworkDataProvider provider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getNetworkIdentity")
            .summary("Get node network identity")
            .description("Retrieves data about the node's network presence")
            .tags(TAG_NODE)
            .response(SC_OK, "Request successful", IDENTITY_RESPONSE_TYPE)
            .build());
    this.network = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get node identity",
      description = "Retrieves data about the node's network presence.",
      tags = {TAG_NODE},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = IdentityResponse.class),
            description = "The identifying information of the node."),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final IdentityData networkIdentity =
        new IdentityData(
            network.getNodeIdAsBase58(),
            network.getEnr(),
            network.getListeningAddresses(),
            network.getDiscoveryAddresses(),
            network.getMetadata());

    request.respondOk(networkIdentity, NO_CACHE);
  }

  static class IdentityData {
    private final String peerId;
    private final Optional<String> enr;
    private final List<String> listeningAddresses;
    private final List<String> discoveryAddresses;
    private final MetadataMessage metadata;

    IdentityData(
        final String peerId,
        final Optional<String> enr,
        final List<String> listeningAddresses,
        final List<String> discoveryAddresses,
        final MetadataMessage metadata) {
      this.peerId = peerId;
      this.enr = enr;
      this.listeningAddresses = listeningAddresses;
      this.discoveryAddresses = discoveryAddresses;
      this.metadata = metadata;
    }

    public String getPeerId() {
      return peerId;
    }

    public Optional<String> getEnr() {
      return enr;
    }

    public List<String> getListeningAddresses() {
      return Collections.unmodifiableList(listeningAddresses);
    }

    public List<String> getDiscoveryAddresses() {
      return Collections.unmodifiableList(discoveryAddresses);
    }

    public MetadataMessage getMetadata() {
      return metadata;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final IdentityData that = (IdentityData) o;
      return Objects.equals(peerId, that.peerId)
          && Objects.equals(enr, that.enr)
          && Objects.equals(listeningAddresses, that.listeningAddresses)
          && Objects.equals(discoveryAddresses, that.discoveryAddresses)
          && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
      return Objects.hash(peerId, enr, listeningAddresses, discoveryAddresses, metadata);
    }
  }
}
