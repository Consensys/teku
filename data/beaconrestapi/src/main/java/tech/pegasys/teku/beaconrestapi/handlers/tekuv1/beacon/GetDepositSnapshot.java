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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon;

import static tech.pegasys.teku.infrastructure.http.ContentTypes.JSON;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.OCTET_STREAM;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EXPERIMENTAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.function.Function;
import tech.pegasys.teku.api.data.DepositTreeSnapshotData;
import tech.pegasys.teku.api.response.v1.teku.GetDepositTreeSnapshotResponse;
import tech.pegasys.teku.api.ssz.DepositTreeSnapshotSerializer;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider;

public class GetDepositSnapshot extends MigratingEndpointAdapter {

  public static final String ROUTE = "/teku/v1/beacon/deposit_snapshot";

  private static final SerializableTypeDefinition<DepositTreeSnapshotData> DEPOSIT_SNAPSHOT_TYPE =
      SerializableTypeDefinition.object(DepositTreeSnapshotData.class)
          .withField(
              "finalized",
              SerializableTypeDefinition.listOf(CoreTypes.BYTES32_TYPE)
                  .withDescription("List of finalized nodes in deposit tree"),
              DepositTreeSnapshotData::getFinalized)
          .withField(
              "deposits",
              CoreTypes.UINT64_TYPE.withDescription("Number of deposits stored in the snapshot"),
              DepositTreeSnapshotData::getDeposits)
          .withField(
              "execution_block_hash",
              CoreTypes.BYTES32_TYPE.withDescription(
                  "Hash of the execution block containing the highest index deposit stored in the snapshot"),
              DepositTreeSnapshotData::getExecutionBlockHash)
          .build();

  public static final SerializableTypeDefinition<DepositTreeSnapshotData>
      DEPOSIT_SNAPSHOT_RESPONSE_TYPE =
          SerializableTypeDefinition.<DepositTreeSnapshotData>object()
              .name("GetDepositSnapshotResponse")
              .withField("data", DEPOSIT_SNAPSHOT_TYPE, Function.identity())
              .build();

  private final Eth1DataProvider eth1DataProvider;

  public GetDepositSnapshot(final Eth1DataProvider eth1DataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getDepositSnapshot")
            .summary("Get finalized DepositTreeSnapshot")
            .description(
                "Latest finalized DepositTreeSnapshot that could be used to reconstruct Deposit merkle tree. "
                    + "See EIP-4881 for details.")
            .tags(TAG_TEKU)
            .response(
                SC_OK,
                "Request successful",
                DEPOSIT_SNAPSHOT_RESPONSE_TYPE,
                DepositTreeSnapshotSerializer.OCTET_TYPE_DEFINITION)
            .withNotFoundResponse()
            .build());
    this.eth1DataProvider = eth1DataProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get finalized DepositTreeSnapshot",
      description =
          "Latest finalized DepositTreeSnapshot that could be used to reconstruct Deposit merkle tree. "
              + "See EIP-4881 for details.",
      tags = {TAG_TEKU, TAG_EXPERIMENTAL},
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = {
              @OpenApiContent(type = JSON, from = GetDepositTreeSnapshotResponse.class),
              @OpenApiContent(type = OCTET_STREAM)
            }),
        @OpenApiResponse(status = RES_NOT_FOUND),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.respondAsync(
        eth1DataProvider
            .getFinalizedDepositTreeSnapshot()
            .thenApply(
                maybeDepositTreeSnapshot -> {
                  if (maybeDepositTreeSnapshot.isEmpty()) {
                    return AsyncApiResponse.respondNotFound();
                  } else {
                    return AsyncApiResponse.respondOk(maybeDepositTreeSnapshot.get());
                  }
                }));
  }
}
