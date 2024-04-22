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

import static tech.pegasys.teku.ethereum.json.types.SharedApiTypes.PUBKEY_API_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.validator.client.restapi.ValidatorRestApi.TAG_GRAFFITI;
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.PARAM_PUBKEY_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.api.Bytes32Parser;
import tech.pegasys.teku.validator.api.UpdatableGraffitiProvider;
import tech.pegasys.teku.validator.client.KeyManager;
import tech.pegasys.teku.validator.client.Validator;

public class GetGraffiti extends RestApiEndpoint {
  static final String ROUTE = "/eth/v1/validator/{pubkey}/graffiti";
  private final KeyManager keyManager;

  public static final DeserializableTypeDefinition<Bytes32> GRAFFITI_TYPE =
      DeserializableTypeDefinition.string(Bytes32.class)
          .formatter(GetGraffiti::processGraffitiString)
          .parser(Bytes32Parser::toBytes32)
          .example("Example graffiti")
          .description("Bytes32 string")
          .format("byte")
          .build();

  private static final SerializableTypeDefinition<GraffitiResponse> GRAFFITI_RESPONSE_TYPE =
      SerializableTypeDefinition.object(GraffitiResponse.class)
          .withOptionalField("pubkey", PUBKEY_API_TYPE, GraffitiResponse::getPublicKey)
          .withField("graffiti", GRAFFITI_TYPE, GraffitiResponse::getGraffiti)
          .build();

  private static final SerializableTypeDefinition<GraffitiResponse> RESPONSE_TYPE =
      SerializableTypeDefinition.object(GraffitiResponse.class)
          .name("GraffitiResponse")
          .withField("data", GRAFFITI_RESPONSE_TYPE, Function.identity())
          .build();

  public GetGraffiti(final KeyManager keyManager) {
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
    this.keyManager = keyManager;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final BLSPublicKey publicKey = request.getPathParameter(PARAM_PUBKEY_TYPE);

    final Optional<Validator> maybeValidator = keyManager.getValidatorByPublicKey(publicKey);
    if (maybeValidator.isEmpty()) {
      request.respondError(SC_NOT_FOUND, "Validator not found");
      return;
    }

    try {
      final UpdatableGraffitiProvider provider =
          (UpdatableGraffitiProvider) maybeValidator.get().getGraffitiProvider();
      request.respondOk(new GraffitiResponse(publicKey, provider.getWithThrowable()));
    } catch (Throwable e) {
      request.respondError(SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  private static String processGraffitiString(final Bytes32 graffiti) {
    return new String(graffiti.toArrayUnsafe(), StandardCharsets.UTF_8).strip().replace("\0", "");
  }

  static class GraffitiResponse {
    private final Optional<BLSPublicKey> publicKey;
    private final Bytes32 graffiti;

    GraffitiResponse(final BLSPublicKey publicKey, final Optional<Bytes32> graffiti) {
      this(publicKey, graffiti.orElse(Bytes32Parser.toBytes32(Bytes.EMPTY.toArray())));
    }

    GraffitiResponse(final BLSPublicKey publicKey, final Bytes32 graffiti) {
      this.publicKey = Optional.of(publicKey);
      this.graffiti = graffiti;
    }

    Optional<BLSPublicKey> getPublicKey() {
      return publicKey;
    }

    Bytes32 getGraffiti() {
      return graffiti;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final GraffitiResponse that = (GraffitiResponse) o;
      return Objects.equals(publicKey, that.publicKey) && Objects.equals(graffiti, that.graffiti);
    }

    @Override
    public int hashCode() {
      return Objects.hash(publicKey, graffiti);
    }
  }
}
