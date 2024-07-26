/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.validator.remote.typedef;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.infrastructure.json.exceptions.BadRequestException;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.validator.remote.typedef.handlers.GetGenesisRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.GetSpecRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.PostStateValidatorsRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.PostVoluntaryExitRequest;

public class OkHttpValidatorMinimalTypeDefClient {
  private final OkHttpClient okHttpClient;
  private final HttpUrl baseEndpoint;
  private final GetGenesisRequest getGenesisRequest;
  private final PostVoluntaryExitRequest postVoluntaryExitRequest;

  private final GetSpecRequest getSpecRequest;
  private final PostStateValidatorsRequest postStateValidatorsRequest;

  public OkHttpValidatorMinimalTypeDefClient(
      final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    this.okHttpClient = okHttpClient;
    this.baseEndpoint = baseEndpoint;
    this.getSpecRequest = new GetSpecRequest(baseEndpoint, okHttpClient);
    this.getGenesisRequest = new GetGenesisRequest(baseEndpoint, okHttpClient);
    this.postStateValidatorsRequest = new PostStateValidatorsRequest(baseEndpoint, okHttpClient);
    this.postVoluntaryExitRequest = new PostVoluntaryExitRequest(baseEndpoint, okHttpClient);
  }

  public Optional<Map<String, String>> getSpec() {
    return getSpecRequest.getSpec();
  }

  public OkHttpClient getOkHttpClient() {
    return okHttpClient;
  }

  public HttpUrl getBaseEndpoint() {
    return baseEndpoint;
  }

  public Optional<GenesisData> getGenesis() {
    return getGenesisRequest
        .getGenesisData()
        .map(
            response ->
                new GenesisData(response.getGenesisTime(), response.getGenesisValidatorsRoot()));
  }

  public Optional<List<StateValidatorData>> postStateValidators(final List<String> validatorIds) {
    return postStateValidatorsRequest
        .postStateValidators(validatorIds)
        .map(ObjectAndMetaData::getData);
  }

  public void sendVoluntaryExit(final SignedVoluntaryExit signedVoluntaryExit)
      throws BadRequestException {
    postVoluntaryExitRequest.sendVoluntaryExit(signedVoluntaryExit);
  }
}
