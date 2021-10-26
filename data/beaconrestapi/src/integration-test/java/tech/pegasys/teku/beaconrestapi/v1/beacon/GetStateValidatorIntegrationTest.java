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

package tech.pegasys.teku.beaconrestapi.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.beacon.GetStateValidatorResponse;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetStateValidator;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetStateValidatorIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldGetValidatorResponseForKnownValidatorFromKnownState() throws IOException {
    final Response response = get("genesis", "1");
    assertThat(response.code()).isEqualTo(SC_OK);
    final GetStateValidatorResponse body =
        jsonProvider.jsonToObject(response.body().string(), GetStateValidatorResponse.class);
    assertThat(body.data.index).isEqualTo(UInt64.ONE);
  }

  @Test
  public void shouldGetNotFoundForValidatorOutOfRange() throws IOException {
    final Response response = get("genesis", "123456789");
    assertThat(response.code()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  public void shouldGetBadRequestForInvalidValidatorId() throws IOException {
    final Response response = get("genesis", "-1");
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  public void shouldGetNotFoundForMissingState() throws IOException {
    final Response response = get("0xdeadbeef", "1");
    assertThat(response.code()).isEqualTo(SC_NOT_FOUND);
  }

  public Response get(final String stateIdString, final String validatorIdString)
      throws IOException {
    return getResponse(
        GetStateValidator.ROUTE
            .replace("{state_id}", stateIdString)
            .replace("{validator_id}", validatorIdString));
  }
}
