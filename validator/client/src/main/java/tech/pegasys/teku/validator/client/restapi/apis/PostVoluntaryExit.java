/*
 * Copyright ConsenSys Software Inc., 2023
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
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EPOCH;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EPOCH_QUERY_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EXPERIMENTAL;
import static tech.pegasys.teku.validator.client.restapi.ValidatorRestApi.TAG_VOLUNTARY_EXIT;
import static tech.pegasys.teku.validator.client.restapi.ValidatorTypes.PARAM_PUBKEY_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.KeyManager;
import tech.pegasys.teku.validator.client.Validator;

public class PostVoluntaryExit extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/{pubkey}/voluntary_exit";

  public static final SerializableTypeDefinition<SignedVoluntaryExit>
      SIGNED_VOLUNTARY_EXIT_RESPONSE_TYPE =
          SerializableTypeDefinition.<SignedVoluntaryExit>object()
              .name("SignedVoluntaryExitResponse")
              .withField(
                  "data",
                  SignedVoluntaryExit.SSZ_SCHEMA.getJsonTypeDefinition(),
                  Function.identity())
              .build();
  private static final ParameterMetadata<UInt64> EPOCH_PARAMETER =
      new ParameterMetadata<>(
          EPOCH, CoreTypes.UINT64_TYPE.withDescription(EPOCH_QUERY_DESCRIPTION));

  private final Spec spec;
  final KeyManager keyManager;
  final ValidatorApiChannel validatorApiChannel;

  public PostVoluntaryExit(
      final Spec spec, final KeyManager keyManager, final ValidatorApiChannel validatorApiChannel) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("signVoluntaryExit")
            .summary("Sign Voluntary Exit")
            .tags(TAG_VOLUNTARY_EXIT, TAG_EXPERIMENTAL)
            .withBearerAuthSecurity()
            .pathParam(PARAM_PUBKEY_TYPE)
            .queryParam(EPOCH_PARAMETER)
            .description(
                "Create a signed voluntary exit message for an active validator, identified by a public key known "
                    + "to the validator client. This endpoint returns a SignedVoluntaryExit object, which can be "
                    + "used to initiate voluntary exit via the beacon node's submitPoolVoluntaryExit endpoint.")
            .response(SC_OK, "Success response", SIGNED_VOLUNTARY_EXIT_RESPONSE_TYPE)
            .withNotFoundResponse()
            .withUnauthorizedResponse()
            .withForbiddenResponse()
            .build());
    this.spec = spec;
    this.keyManager = keyManager;
    this.validatorApiChannel = validatorApiChannel;
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final BLSPublicKey publicKey = request.getPathParameter(PARAM_PUBKEY_TYPE);
    final UInt64 epoch =
        request
            .getOptionalQueryParameter(EPOCH_PARAMETER)
            .orElse(UInt64.ZERO); // TODO current epoch

    final SafeFuture<SignedVoluntaryExit> future = getVoluntaryExit(publicKey, epoch);
    request.respondAsync(future.thenApply(AsyncApiResponse::respondOk));
  }

  private SafeFuture<SignedVoluntaryExit> getVoluntaryExit(
      final BLSPublicKey publicKey, final UInt64 epoch) {
    return validatorApiChannel
        .getGenesisData()
        .thenCompose(
            genesisData -> {
              if (genesisData.isEmpty()) {
                throw new InvalidConfigurationException(
                    "Unable to fetch genesis data, cannot generate an exit.");
              }

              final Bytes32 genesisRoot = genesisData.get().getGenesisValidatorsRoot();
              final Fork fork = spec.getForkSchedule().getFork(epoch);
              final ForkInfo forkInfo = new ForkInfo(fork, genesisRoot);

              return validatorApiChannel
                  .getValidatorIndices(Set.of(publicKey))
                  .thenApply(
                      indicesMap -> {
                        final int validatorIndex = indicesMap.get(publicKey);
                        final Validator validator =
                            keyManager.getActiveValidatorKeys().stream()
                                .filter(v -> v.getPublicKey().equals(publicKey))
                                .findFirst()
                                .orElseThrow(
                                    () ->
                                        new BadRequestException(
                                            "Validator not found with public key value."));
                        final VoluntaryExit message =
                            new VoluntaryExit(epoch, UInt64.valueOf(validatorIndex));
                        final BLSSignature signature =
                            Optional.ofNullable(validator)
                                .orElseThrow()
                                .getSigner()
                                .signVoluntaryExit(message, forkInfo)
                                .join();

                        return new SignedVoluntaryExit(message, signature);
                      });
            });
  }
}
