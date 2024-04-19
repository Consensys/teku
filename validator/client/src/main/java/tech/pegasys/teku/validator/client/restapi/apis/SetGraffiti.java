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

package tech.pegasys.teku.validator.client.restapi.apis;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.validator.client.restapi.ValidatorRestApi.TAG_GRAFFITI;
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.PARAM_PUBKEY_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.NotImplementedException;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class SetGraffiti extends RestApiEndpoint {

  public SetGraffiti() {
    super(
        EndpointMetadata.post(GetGraffiti.ROUTE)
            .operationId("setGraffiti")
            .summary("Set Graffiti")
            .description("Set the graffiti for an individual validator.")
            .tags(TAG_GRAFFITI)
            .withBearerAuthSecurity()
            .pathParam(PARAM_PUBKEY_TYPE)
            .requestBodyType(STRING_TYPE.withDescription("Graffiti string"))
            .response(SC_NO_CONTENT, "Successfully updated graffiti.")
            .withAuthenticationResponses()
            .withNotFoundResponse()
            .withNotImplementedResponse()
            .build());
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    throw new NotImplementedException("Not Implemented");
  }
}
