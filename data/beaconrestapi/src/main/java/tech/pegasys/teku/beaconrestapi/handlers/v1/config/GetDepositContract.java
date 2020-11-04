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

package tech.pegasys.teku.beaconrestapi.handlers.v1.config;

import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_CONFIG;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import tech.pegasys.teku.api.response.v1.config.GetDepositContractResponse;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.config.Eth1Address;

public class GetDepositContract implements Handler {
  public static final String ROUTE = "/eth/v1/config/deposit_contract";
  private Optional<String> depositContractResponse;
  private final String depositContractAddress;
  private final JsonProvider jsonProvider;

  public GetDepositContract(
      final Optional<Eth1Address> depositContractAddress, final JsonProvider jsonProvider) {
    this.jsonProvider = jsonProvider;
    this.depositContractResponse = Optional.empty();
    this.depositContractAddress =
        depositContractAddress
            .map(Eth1Address::toHexString)
            .orElse("0xdddddddddddddddddddddddddddddddddddddddd");
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get deposit contract address",
      tags = {TAG_CONFIG},
      description = "Retrieve deposit contract address and genesis fork version.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetDepositContractResponse.class)),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    if (depositContractResponse.isEmpty()) {
      this.depositContractResponse =
          Optional.of(
              jsonProvider.objectToJSON(
                  new GetDepositContractResponse(
                      Constants.DEPOSIT_CHAIN_ID, depositContractAddress)));
    }
    ctx.status(SC_OK);
    ctx.result(this.depositContractResponse.orElse(""));
  }
}
