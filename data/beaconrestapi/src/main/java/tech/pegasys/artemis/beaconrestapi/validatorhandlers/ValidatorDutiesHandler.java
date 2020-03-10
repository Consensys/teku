package tech.pegasys.artemis.beaconrestapi.validatorhandlers;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.CacheControlUtils.getMaxAgeForBeaconState;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.ACTIVE;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.EPOCH;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.NO_CONTENT_PRE_GENESIS;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_NO_CONTENT;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.artemis.beaconrestapi.SingleQueryParameterUtils.getParameterValueAsUnsignedLong;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.primitives.UnsignedLong;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.api.schema.BeaconState;
import tech.pegasys.artemis.beaconrestapi.schema.BadRequest;
import tech.pegasys.artemis.beaconrestapi.schema.ValidatorDuties;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.util.async.SafeFuture;

public class ValidatorDutiesHandler implements Handler {
  private final ChainDataProvider chainDataProvider;

  public ValidatorDutiesHandler(
      final ChainDataProvider chainDataProvider, final JsonProvider jsonProvider) {
    this.chainDataProvider = chainDataProvider;
    this.jsonProvider = jsonProvider;
  }

  public static final String ROUTE = "/validator/duties";
  private final JsonProvider jsonProvider;

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Returns validator duties that match the specified query.",
      tags = {TAG_VALIDATOR},
      description =
          "Returns validator duties for the given epoch.",
      queryParams = {
          @OpenApiParam(
              name = EPOCH,
              description = "Epoch to query. If not specified, current epoch is used.")
      },
      responses = {
          @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = ValidatorDuties.class)),
          @OpenApiResponse(status = RES_NO_CONTENT, description = NO_CONTENT_PRE_GENESIS),
          @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })

  @Override
  public void handle(Context ctx) throws Exception {
    final Map<String, List<String>> parameters = ctx.queryParamMap();
    try {
      if (!chainDataProvider.isStoreAvailable()) {
        ctx.status(SC_NO_CONTENT);
        return;
      }

      final SafeFuture<Optional<BeaconState>> future = getStateFuture(parameters);

      ctx.result(
          future.thenApplyChecked(
              state -> handleResponseContext(ctx, state)));
    } catch (final IllegalArgumentException e) {
      ctx.result(jsonProvider.objectToJSON(new BadRequest(e.getMessage())));
      ctx.status(SC_BAD_REQUEST);
    }
  }


  private SafeFuture<Optional<BeaconState>> getStateFuture(Map<String, List<String>> parameters) {
    if (parameters.containsKey(EPOCH)) {
      UnsignedLong epoch = getParameterValueAsUnsignedLong(parameters, EPOCH);
      UnsignedLong slot = BeaconStateUtil.compute_start_slot_at_epoch(epoch);
      return chainDataProvider.getStateAtSlot(slot);
    }
    Optional<Bytes32> blockRoot = chainDataProvider.getBestBlockRoot();
    if (blockRoot.isPresent()) {
      return chainDataProvider.getStateByBlockRoot(blockRoot.get());
    }

    return completedFuture(Optional.empty());
  }

  private String handleResponseContext(
      Context ctx,
      Optional<BeaconState> optionalState)
      throws JsonProcessingException {
    if (optionalState.isEmpty()) {
      return jsonProvider.objectToJSON(List.of());
    } else {
      final BeaconState state = optionalState.get();
      final ValidatorDuties result =
          new ValidatorDuties();

      ctx.header(Header.CACHE_CONTROL, getMaxAgeForBeaconState(chainDataProvider, state));
      return jsonProvider.objectToJSON(result);
    }
  }
}
