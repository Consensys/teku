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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.COMMITTEE_INDEX;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.COMMITTEE_INDEX_QUERY_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT_QUERY_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.response.v1.beacon.GetAttestationsResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;

public class GetAttestations extends MigratingEndpointAdapter {
  public static final String ROUTE = "/eth/v1/beacon/pool/attestations";
  private final NodeDataProvider nodeDataProvider;

  private static final ParameterMetadata<UInt64> SLOT_PARAMETER =
      new ParameterMetadata<>(SLOT, CoreTypes.UINT64_TYPE.withDescription(SLOT_QUERY_DESCRIPTION));

  private static final ParameterMetadata<UInt64> COMMITTEE_INDEX_PARAMETER =
      new ParameterMetadata<>(
          COMMITTEE_INDEX,
          CoreTypes.UINT64_TYPE.withDescription(COMMITTEE_INDEX_QUERY_DESCRIPTION));

  public GetAttestations(final DataProvider dataProvider, Spec spec) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getAttestations")
            .summary("Get attestations")
            .description(
                "Retrieves attestations known by the node but not necessarily incorporated into any block.")
            .tags(TAG_BEACON)
            .queryParam(SLOT_PARAMETER)
            .queryParam(COMMITTEE_INDEX_PARAMETER)
            .response(SC_OK, "Request successful", getResponseType(spec.getGenesisSpecConfig()))
            .withNotFoundResponse()
            .build());
    this.nodeDataProvider = dataProvider.getNodeDataProvider();
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get attestations",
      tags = {TAG_BEACON},
      description =
          "Retrieves attestations known by the node but not necessarily incorporated into any block.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetAttestationsResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    Optional<UInt64> slot = request.getOptionalQueryParameter(SLOT_PARAMETER);
    Optional<UInt64> committeeIndex = request.getOptionalQueryParameter(COMMITTEE_INDEX_PARAMETER);

    List<Attestation> attestations = nodeDataProvider.getAttestations(slot, committeeIndex);
    request.respondOk(attestations);
  }

  private static SerializableTypeDefinition<List<Attestation>> getResponseType(
      SpecConfig specConfig) {
    return SerializableTypeDefinition.<List<Attestation>>object()
        .name("GetPoolAttestationsResponse")
        .withField(
            "data",
            listOf(new Attestation.AttestationSchema(specConfig).getJsonTypeDefinition()),
            Function.identity())
        .build();
  }
}
