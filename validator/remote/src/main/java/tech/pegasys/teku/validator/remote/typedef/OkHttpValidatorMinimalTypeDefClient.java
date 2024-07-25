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

import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.validator.remote.typedef.handlers.GetGenesisRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.GetSpecRequest;
import tech.pegasys.teku.validator.remote.typedef.handlers.PostVoluntaryExitRequest;

public class OkHttpValidatorMinimalTypeDefClient {
  private final OkHttpClient okHttpClient;
  private final HttpUrl baseEndpoint;
  private final GetGenesisRequest getGenesisRequest;
  private final PostVoluntaryExitRequest postVoluntaryExitRequest;

  private final GetSpecRequest getSpecRequest;

  public OkHttpValidatorMinimalTypeDefClient(
      final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    this.okHttpClient = okHttpClient;
    this.baseEndpoint = baseEndpoint;
    this.getSpecRequest = new GetSpecRequest(baseEndpoint, okHttpClient);

    this.getGenesisRequest = new GetGenesisRequest(baseEndpoint, okHttpClient);
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

  public Optional<HttpErrorResponse> sendVoluntaryExit(
      final SignedVoluntaryExit signedVoluntaryExit) {
    return postVoluntaryExitRequest.sendVoluntaryExit(signedVoluntaryExit);
  }
}
