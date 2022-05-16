/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_GENESIS;

import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class GetGenesisRequest extends AbstractTypeDefRequest {

  private static final DeserializableTypeDefinition<GenesisData> GENESIS_DATA =
      DeserializableTypeDefinition.object(GenesisData.class, GenesisData.Builder.class)
          .finisher(GenesisData.Builder::build)
          .initializer(GenesisData::builder)
          .withField(
              "genesis_time",
              UINT64_TYPE,
              GenesisData::getGenesisTime,
              GenesisData.Builder::genesisTime)
          .withField(
              "genesis_validators_root",
              BYTES32_TYPE,
              GenesisData::getGenesisValidatorsRoot,
              GenesisData.Builder::genesisValidatorsRoot)
          .build();
  private static final DeserializableTypeDefinition<GetGenesisResponse> GENESIS_RESPONSE =
      DeserializableTypeDefinition.object(GetGenesisResponse.class)
          .name("GetGenesisResponse")
          .initializer(GetGenesisResponse::new)
          .withField("data", GENESIS_DATA, GetGenesisResponse::getData, GetGenesisResponse::setData)
          .build();

  public GetGenesisRequest(final OkHttpClient okHttpClient, final HttpUrl baseEndpoint) {
    super(baseEndpoint, okHttpClient);
  }

  public Optional<GenesisData> getGenesisData() {
    final Optional<GetGenesisResponse> response =
        get(GET_GENESIS, new ResponseHandler<>(GENESIS_RESPONSE));
    return response.map(GetGenesisResponse::getData);
  }

  private static class GetGenesisResponse {
    private GenesisData data;

    GetGenesisResponse() {}

    public GenesisData getData() {
      return data;
    }

    public void setData(final GenesisData data) {
      this.data = data;
    }
  }
}
