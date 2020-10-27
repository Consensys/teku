/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_ID;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.PARAM_STATE_ID;

import io.javalin.http.Context;
import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.beaconrestapi.ListQueryParameterUtils;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;

public class StateValidatorsUtil {

  public List<Integer> parseValidatorsParam(final ChainDataProvider provider, final Context ctx) {
    return ListQueryParameterUtils.getParameterAsStringList(ctx.queryParamMap(), PARAM_ID).stream()
        .flatMap(
            validatorParameter -> provider.validatorParameterToIndex(validatorParameter).stream())
        .collect(Collectors.toList());
  }

  public UInt64 parseStateIdPathParam(final ChainDataProvider provider, final Context ctx) {
    return provider
        .stateParameterToSlot(ctx.pathParamMap().get(PARAM_STATE_ID))
        .orElseThrow(ChainDataUnavailableException::new);
  }
}
