/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.validator.client.restapi;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.StringUtils;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.exceptions.ServiceUnavailableException;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.restapi.RestApi;
import tech.pegasys.teku.infrastructure.restapi.RestApiBuilder;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.validator.client.KeyManager;
import tech.pegasys.teku.validator.client.restapi.apis.DeleteKeys;
import tech.pegasys.teku.validator.client.restapi.apis.GetKeys;
import tech.pegasys.teku.validator.client.restapi.apis.PostKeys;

public class ValidatorRestApi {
  public static RestApi create(final ValidatorRestApiConfig config, final KeyManager keyManager) {
    return new RestApiBuilder()
        .openApiInfo(
            openApi ->
                openApi
                    .title(
                        StringUtils.capitalize(
                            VersionProvider.CLIENT_IDENTITY + " Validator Rest API"))
                    .version(VersionProvider.IMPLEMENTATION_VERSION)
                    .description("An implementation of the key management standard Rest API.")
                    .license("Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0.html"))
        .openApiDocsEnabled(config.isRestApiDocsEnabled())
        .listenAddress(config.getRestApiInterface())
        .port(config.getRestApiPort())
        .maxUrlLength(config.getMaxUrlLength())
        .corsAllowedOrigins(config.getRestApiCorsAllowedOrigins())
        .hostAllowlist(config.getRestApiHostAllowlist())
        .exceptionHandler(
            ServiceUnavailableException.class,
            (throwable, url) ->
                new HttpErrorResponse(SC_SERVICE_UNAVAILABLE, "Service unavailable"))
        .exceptionHandler(
            BadRequestException.class,
            (throwable, url) -> new HttpErrorResponse(SC_BAD_REQUEST, throwable.getMessage()))
        .exceptionHandler(
            JsonProcessingException.class,
            (throwable, url) -> new HttpErrorResponse(SC_BAD_REQUEST, throwable.getMessage()))
        .endpoint(new GetKeys(keyManager))
        .endpoint(new DeleteKeys(keyManager))
        .endpoint(new PostKeys(keyManager))
        .build();
  }
}
