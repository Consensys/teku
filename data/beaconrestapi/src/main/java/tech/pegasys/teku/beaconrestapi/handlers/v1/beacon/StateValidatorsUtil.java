package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import io.javalin.http.Context;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.beaconrestapi.ListQueryParameterUtils;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;

import java.util.List;
import java.util.stream.Collectors;

import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_STATE_ID;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_VALIDATOR_ID;

public class StateValidatorsUtil {

  public List<Integer> parseValidatorsParam(final ChainDataProvider provider, final Context ctx) {
    return ListQueryParameterUtils.getParameterAsStringList(ctx.queryParamMap(), PARAM_VALIDATOR_ID)
            .stream()
            .flatMap(
                    validatorParameter -> provider.validatorParameterToIndex(validatorParameter).stream())
            .collect(Collectors.toList());
  }

  public UInt64 parseSlotParam(final ChainDataProvider provider, final Context ctx) {
    return provider
            .stateParameterToSlot(ctx.pathParamMap().get(PARAM_STATE_ID))
            .orElseThrow(ChainDataUnavailableException::new);
  }
}
