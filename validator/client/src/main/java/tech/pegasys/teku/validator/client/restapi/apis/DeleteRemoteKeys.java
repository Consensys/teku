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

package tech.pegasys.teku.validator.client.restapi.apis;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.NotImplementedException;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.client.KeyManager;
import tech.pegasys.teku.validator.client.restapi.ValidatorTypes;

public class DeleteRemoteKeys extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/remotekeys";
  private final KeyManager keyManager;

  public DeleteRemoteKeys(final KeyManager keyManager) {
    super(
        EndpointMetadata.delete(ROUTE)
            .operationId("DeleteRemoteKeys")
            .summary("Delete Remote Keys")
            .withBearerAuthSecurity()
            .description(
                "DELETE must delete all keys from `request.pubkeys` that are known to the validator client and exist in its persistent storage."
                    + "<br>DELETE should never return a 404 response, even if all pubkeys from request.pubkeys have no existing keystores.")
            .requestBodyType(ValidatorTypes.DELETE_KEYS_REQUEST)
            .response(SC_OK, "Success response", ValidatorTypes.DELETE_REMOTE_KEYS_RESPONSE_TYPE)
            .withAuthenticationResponses()
            .build());

    this.keyManager = keyManager;
  }

  @Override
  public void handle(final RestApiRequest request) throws JsonProcessingException {
    throw new NotImplementedException();
  }
}
