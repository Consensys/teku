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

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.GetStateValidatorResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.api.schema.Validator;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetStateValidatorTest extends AbstractBeaconHandlerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final GetStateValidator handler = new GetStateValidator(chainDataProvider, jsonProvider);
  private final Validator validator = new Validator(dataStructureUtil.randomValidator());
  private final ValidatorResponse validatorResponse =
      new ValidatorResponse(
          ONE,
          UInt64.valueOf("32000000000"),
          ValidatorStatus.active_ongoing,
          new Validator(
              validator.pubkey,
              validator.withdrawal_credentials,
              UInt64.valueOf("32000000000"),
              false,
              ZERO,
              ZERO,
              FAR_FUTURE_EPOCH,
              FAR_FUTURE_EPOCH));

  @Test
  public void shouldGetValidatorFromState() throws Exception {
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "head", "validator_id", "1"));
    when(chainDataProvider.getStateValidator("head", "1"))
        .thenReturn(SafeFuture.completedFuture(Optional.of(validatorResponse)));
    handler.handle(context);
    GetStateValidatorResponse response = getResponseFromFuture(GetStateValidatorResponse.class);
    assertThat(response.data).isEqualTo(validatorResponse);
  }

  @Test
  public void shouldGetNotFoundForMissingState() throws Exception {
    when(context.pathParamMap()).thenReturn(Map.of("state_id", "1", "validator_id", "1"));
    when(chainDataProvider.getStateValidator("1", "1"))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    handler.handle(context);
    verify(context).status(SC_NOT_FOUND);
  }
}
