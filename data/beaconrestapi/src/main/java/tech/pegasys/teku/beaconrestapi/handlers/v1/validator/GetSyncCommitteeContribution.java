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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsBytes32;
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsInt;
import static tech.pegasys.teku.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsUInt64;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.BEACON_BLOCK_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SUBCOMMITTEE_INDEX;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.response.v1.validator.GetSyncCommitteeContributionResponse;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeContribution;
import tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.constants.NetworkConstants;

public class GetSyncCommitteeContribution extends AbstractHandler {
  public static final String ROUTE = "/eth/v1/validator/sync_committee_contribution";

  private final ValidatorDataProvider provider;

  public GetSyncCommitteeContribution(
      final DataProvider dataProvider, final JsonProvider jsonProvider) {
    super(jsonProvider);
    this.provider = dataProvider.getValidatorDataProvider();
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Produce a sync committee contribution",
      tags = {TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED},
      queryParams = {
        @OpenApiParam(
            name = SLOT,
            description =
                "`uint64` The slot for which a sync committee contribution should be created.",
            required = true),
        @OpenApiParam(
            name = SUBCOMMITTEE_INDEX,
            description = "`uint64` The subcommittee index for which to produce the contribution.",
            required = true),
        @OpenApiParam(
            name = BEACON_BLOCK_ROOT,
            description = "`bytes32` The block root for which to produce the contribution.",
            required = true)
      },
      description =
          "Returns a `SyncCommitteeContribution` that is the aggregate of `SyncCommitteeMessage` "
              + "values known to this node matching the specified slot, subcommittee index and beacon block root.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetSyncCommitteeContributionResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid request syntax."),
        @OpenApiResponse(
            status = RES_NOT_FOUND,
            description = "No matching sync committee messages were found"),
        @OpenApiResponse(status = RES_INTERNAL_ERROR, description = "Beacon node internal error.")
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    try {
      final Map<String, List<String>> parameters = ctx.queryParamMap();
      if (parameters.size() < 3) {
        throw new IllegalArgumentException(
            String.format(
                "Please specify all of %s, %s and %s",
                SLOT, SUBCOMMITTEE_INDEX, BEACON_BLOCK_ROOT));
      }
      final UInt64 slot = getParameterValueAsUInt64(parameters, SLOT);
      final Bytes32 blockRoot = getParameterValueAsBytes32(parameters, BEACON_BLOCK_ROOT);
      final int subcommitteeIndex = getParameterValueAsInt(parameters, SUBCOMMITTEE_INDEX);
      if (subcommitteeIndex < 0
          || subcommitteeIndex >= NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT) {
        throw new IllegalArgumentException(
            String.format(
                "Subcommittee index needs to be between 0 and %s, %s is outside of this range.",
                NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT - 1, subcommitteeIndex));
      }

      if (provider.isPhase0Slot(slot)) {
        throw new IllegalArgumentException(String.format("Slot %s is not an Altair slot", slot));
      }

      final SafeFuture<Optional<SyncCommitteeContribution>> future =
          provider.createSyncCommitteeContribution(slot, subcommitteeIndex, blockRoot);
      handleOptionalResult(ctx, future, this::processResult, SC_NOT_FOUND);
    } catch (final IllegalArgumentException e) {
      ctx.json(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }

  private Optional<String> processResult(
      final Context ctx, final SyncCommitteeContribution contribution)
      throws JsonProcessingException {
    return Optional.of(
        jsonProvider.objectToJSON(new GetSyncCommitteeContributionResponse(contribution)));
  }
}
