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

package tech.pegasys.teku.beaconrestapi.handlers.v1.debug;

import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_DEBUG;

import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.response.v1.debug.GetChainHeadsResponse;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;

public class GetChainHeadsV1 extends GetChainHeads {
  public static final String ROUTE = "/eth/v1/debug/beacon/heads";

  private static final SerializableTypeDefinition<ProtoNodeData> CHAIN_HEAD_TYPE_V1 =
      SerializableTypeDefinition.object(ProtoNodeData.class)
          .name("ChainHead")
          .withField("slot", CoreTypes.UINT64_TYPE, ProtoNodeData::getSlot)
          .withField("root", CoreTypes.BYTES32_TYPE, ProtoNodeData::getRoot)
          .build();

  public GetChainHeadsV1(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  public GetChainHeadsV1(final ChainDataProvider chainDataProvider) {
    super(chainDataProvider, ROUTE, CHAIN_HEAD_TYPE_V1);
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get fork choice leaves",
      tags = {TAG_DEBUG},
      deprecated = true,
      description = "Retrieves all possible chain heads (leaves of fork choice tree).\n\n"
          + "Deprecated - use `/eth/v2/debug/beacon/heads",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetChainHeadsResponse.class)),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    adapt(ctx);
  }
}
