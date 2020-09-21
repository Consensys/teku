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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.util.config.Constants.FAR_FUTURE_EPOCH;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.GetStateValidatorResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.api.schema.Validator;
import tech.pegasys.teku.beaconrestapi.AbstractBeaconHandlerTest;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetStateValidatorTest extends AbstractBeaconHandlerTest {
  final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  final GetStateValidator handler = new GetStateValidator(chainDataProvider, jsonProvider);
  Validator validator = new Validator(dataStructureUtil.randomValidator());
  final ValidatorResponse validatorResponse =
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
    when(chainDataProvider.getValidatorDetails(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(validatorResponse)));
    handler.handle(context);
    GetStateValidatorResponse response = getResponseFromFuture(GetStateValidatorResponse.class);
    assertThat(response.data).isEqualTo(validatorResponse);
  }
}
