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

import static java.util.Collections.emptySet;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus.active_exiting;
import static tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus.active_ongoing;
import static tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus.withdrawal_done;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.migrated.StateValidatorData;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class GetStateValidatorsTest extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {
  private GetStateValidators handler;

  @BeforeEach
  void setup() {
    initialise(SpecMilestone.ALTAIR);
    genesis();

    handler = new GetStateValidators(chainDataProvider);
  }

  @Test
  public void shouldGetValidatorFromState() throws Exception {
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .pathParameter("state_id", "head")
            .listQueryParameter("id", List.of("1", "2", "3,4"))
            .build();

    final ObjectAndMetaData<List<StateValidatorData>> expectedResponse =
        chainDataProvider
            .getStateValidators("head", List.of("1", "2", "3", "4"), emptySet())
            .get()
            .orElseThrow();

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @Test
  public void shouldGetValidatorFromStateWithList() throws Exception {
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .pathParameter("state_id", "head")
            .listQueryParameter("id", List.of("1", "2"))
            .listQueryParameter(
                "status", List.of("active_ongoing", "active_exiting, withdrawal_done"))
            .build();

    final ObjectAndMetaData<List<StateValidatorData>> expectedResponse =
        chainDataProvider
            .getStateValidators(
                "head", List.of("1", "2"), Set.of(active_ongoing, active_exiting, withdrawal_done))
            .get()
            .orElseThrow();

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @Test
  public void shouldGetBadRequestForInvalidState() {
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .pathParameter("state_id", "invalid")
            .listQueryParameter("id", List.of("1"))
            .build();

    assertThatThrownBy(() -> handler.handleRequest(request))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Invalid state");
  }
}
