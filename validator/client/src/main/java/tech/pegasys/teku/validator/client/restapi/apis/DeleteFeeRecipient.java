/*
 * Copyright ConsenSys Software Inc., 2022
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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.validator.client.restapi.ValidatorRestApi.TAG_FEE_RECIPIENT;
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.PARAM_PUBKEY_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.client.BeaconProposerPreparer;

public class DeleteFeeRecipient extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/{pubkey}/feerecipient";
  private final Optional<BeaconProposerPreparer> beaconProposerPreparer;

  public DeleteFeeRecipient(final Optional<BeaconProposerPreparer> beaconProposerPreparer) {
    super(
        EndpointMetadata.delete(ROUTE)
            .operationId("DeleteFeeRecipient")
            .summary("Delete configured fee recipient")
            .withBearerAuthSecurity()
            .tags(TAG_FEE_RECIPIENT)
            .pathParam(PARAM_PUBKEY_TYPE)
            .description("Delete a configured fee recipient mapping for the specified public key.")
            .response(SC_NO_CONTENT, "Success")
            .response(
                SC_FORBIDDEN,
                "The fee recipient is set in configuration, and cannot be updated or removed via api.")
            .withAuthenticationResponses()
            .withNotFoundResponse()
            .build());
    this.beaconProposerPreparer = beaconProposerPreparer;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final BLSPublicKey publicKey = request.getPathParameter(PARAM_PUBKEY_TYPE);
    if (beaconProposerPreparer.orElseThrow().getFeeRecipient(publicKey).isEmpty()) {
      request.respondError(SC_NOT_FOUND, "Fee recipient not found");
      return;
    }
    if (!beaconProposerPreparer.orElseThrow().deleteFeeRecipient(publicKey)) {
      request.respondError(SC_FORBIDDEN, "Fee recipient for public key could not be removed.");
      return;
    }
    request.respondWithCode(SC_NO_CONTENT);
  }
}
