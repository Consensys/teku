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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.types.DeserializableTypeDefinition;
import tech.pegasys.teku.validator.client.restapi.ValidatorTypes;

public class DeleteKeys extends RestApiEndpoint {
  private static final Logger LOG = LogManager.getLogger();
  public static final String ROUTE = "/eth/v1/keystores";

  public DeleteKeys() {
    super(
        EndpointMetadata.delete(ROUTE)
            .operationId("DeleteKeys")
            .summary("Delete Keys")
            .description(
                "DELETE must delete all keys from `request.pubkeys` that are known to the key manager."
                    + "<br>Additionally, DELETE must fetch the slashing protection data for the requested keys, "
                    + "which must be retained after the response has been sent. "
                    + "<br>In the case of two identical delete requests being made, both will have access to slashing protection data."
                    + "<br>In a single atomic sequential operation the key manager must:"
                    + "<br>1. Guarantee that key(s) can not produce any more signatures; only then"
                    + "<br>2. Delete key(s) and serialize its associated slashing protection data"
                    + "<br><br>DELETE should never return a 404 response, even if all pubkeys from `request.pubkeys` have no key stores "
                    + "or slashing protection data.")
            .requestBodyType(DeserializableTypeDefinition.listOf(ValidatorTypes.PUBKEY_TYPE))
            .response(SC_OK, "Success response", ValidatorTypes.DELETE_KEYS_RESPONSE_TYPE)
            .withAuthenticationResponses()
            .build());
  }

  @Override
  public void handle(final RestApiRequest request) throws JsonProcessingException {
    List<BLSPublicKey> keys = request.getRequestBody();
    for (BLSPublicKey key : keys) {
      LOG.debug("Delete key: {}", key.toBytesCompressed().toShortHexString());
    }
    request.respondError(SC_INTERNAL_SERVER_ERROR, "Not implemented");
  }
}
