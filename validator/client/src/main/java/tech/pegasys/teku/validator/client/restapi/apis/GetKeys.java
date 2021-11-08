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
import java.util.List;
import java.util.function.Supplier;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.client.restapi.ValidatorTypes;

public class GetKeys extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/keystores";
  private final Supplier<List<BLSPublicKey>> validatorKeysSupplier;

  public GetKeys(Supplier<List<BLSPublicKey>> validatorKeysSupplier) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("ListKeys")
            .summary("List Keys")
            .description(
                "List all validating pubkeys known to and decrypted by this keymanager binary")
            .response(SC_OK, "Success response", ValidatorTypes.LIST_KEYS_RESPONSE_TYPE)
            .withAuthenticationResponses()
            .build());
    this.validatorKeysSupplier = validatorKeysSupplier;
  }

  @Override
  public void handle(final RestApiRequest request) throws JsonProcessingException {
    request.respondOk(validatorKeysSupplier.get());
  }
}
