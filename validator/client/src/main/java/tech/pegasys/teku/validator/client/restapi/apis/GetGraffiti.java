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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.validator.client.restapi.ValidatorRestApi.TAG_GRAFFITI;
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.PARAM_PUBKEY_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.NotImplementedException;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class GetGraffiti extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/{pubkey}/graffiti";

  private static final SerializableTypeDefinition<GraffitiResponse> GRAFFITI_TYPE =
      SerializableTypeDefinition.object(GraffitiResponse.class)
          .withOptionalField("pubkey", STRING_TYPE, GraffitiResponse::getPublicKey)
          .withField("graffiti", STRING_TYPE, GraffitiResponse::getGraffiti)
          .build();

  private static final SerializableTypeDefinition<GraffitiResponse> RESPONSE_TYPE =
      SerializableTypeDefinition.object(GraffitiResponse.class)
          .name("GraffitiResponse")
          .withField("data", GRAFFITI_TYPE, Function.identity())
          .build();

  public GetGraffiti() {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getGraffiti")
            .summary("Get Graffiti")
            .description(
                "Get the graffiti for an individual validator. If no graffiti is set explicitly, returns the process-wide default.")
            .tags(TAG_GRAFFITI)
            .withBearerAuthSecurity()
            .pathParam(PARAM_PUBKEY_TYPE)
            .response(SC_OK, "Success response", RESPONSE_TYPE)
            .withAuthenticationResponses()
            .withNotFoundResponse()
            .build());
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    throw new NotImplementedException("Not implemented");
  }

  static class GraffitiResponse {
    private final Optional<String> publicKey;
    private final String graffiti;

    GraffitiResponse(final BLSPublicKey publicKey, final String graffiti) {
      this.publicKey = Optional.of(publicKey.toHexString());
      this.graffiti = graffiti;
    }

    Optional<String> getPublicKey() {
      return publicKey;
    }

    String getGraffiti() {
      return graffiti;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      GraffitiResponse that = (GraffitiResponse) o;
      return Objects.equals(publicKey, that.publicKey) && Objects.equals(graffiti, that.graffiti);
    }

    @Override
    public int hashCode() {
      return Objects.hash(publicKey, graffiti);
    }
  }
}
