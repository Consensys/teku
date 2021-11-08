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

package tech.pegasys.teku.validator.restapi.apis;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Set;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.types.CoreTypes;
import tech.pegasys.teku.validator.restapi.ValidatorTypes;

public class GetKeys extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/keystores";
  private final Set<BLSPublicKey> validatorKeys;

  public GetKeys(Set<BLSPublicKey> validatorKeys) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("ListKeys")
            .summary("List Keys.")
            .description(
                "List all validating pubkeys known to and decrypted by this keymanager binary")
            .response(SC_OK, "Success response", ValidatorTypes.LIST_KEYS_RESPONSE_TYPE)
            .response(
                SC_UNAUTHORIZED,
                "Unauthorized, no token is found",
                CoreTypes.HTTP_ERROR_RESPONSE_TYPE)
            .response(
                SC_FORBIDDEN,
                "Forbidden, a token is found but is invalid",
                CoreTypes.HTTP_ERROR_RESPONSE_TYPE)
            .response(
                SC_INTERNAL_SERVER_ERROR,
                "Internal server error",
                CoreTypes.HTTP_ERROR_RESPONSE_TYPE)
            .build());
    this.validatorKeys = validatorKeys;
  }

  @Override
  public void handle(final RestApiRequest request) throws JsonProcessingException {
    request.respondOk(List.copyOf(validatorKeys));
  }
}
