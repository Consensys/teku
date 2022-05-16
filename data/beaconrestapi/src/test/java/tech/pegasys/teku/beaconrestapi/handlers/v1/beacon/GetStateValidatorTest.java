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
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.migrated.StateValidatorData;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class GetStateValidatorTest extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {
  private GetStateValidator handler;
  private final StubRestApiRequest request =
      StubRestApiRequest.builder()
          .pathParameter("state_id", "head")
          .pathParameter("validator_id", "1")
          .build();
  private ObjectAndMetaData<StateValidatorData> responseData;

  @BeforeEach
  void setup() throws ExecutionException, InterruptedException {
    initialise(SpecMilestone.ALTAIR);
    genesis();
    handler = new GetStateValidator(chainDataProvider);

    final Optional<StateAndMetaData> stateAndMetaData =
        chainDataProvider.getBeaconStateAndMetadata("head").get();
    final BeaconState beaconState = stateAndMetaData.orElseThrow().getData();

    final StateValidatorData data =
        new StateValidatorData(
            UInt64.valueOf(1),
            beaconState.getBalances().get(1).get(),
            ValidatorStatus.active_ongoing,
            beaconState.getValidators().get(1));

    responseData = new ObjectAndMetaData<>(data, spec.getGenesisSpec().getMilestone(), false, true);
  }

  @Test
  public void shouldGetValidatorFromState() throws Exception {
    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(responseData);
  }

  @Test
  public void shouldGetNotFoundForMissingState() throws Exception {
    final StubRestApiRequest request =
        StubRestApiRequest.builder()
            .pathParameter("state_id", dataStructureUtil.randomBytes32().toHexString())
            .pathParameter("validator_id", "1")
            .build();

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_FOUND);
    assertThat(request.getResponseBody())
        .isEqualTo(new HttpErrorResponse(SC_NOT_FOUND, "Not found"));
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    final String data = getResponseStringFromMetadata(handler, HttpStatusCodes.SC_OK, responseData);
    String expected =
        Resources.toString(
            Resources.getResource(GetStateValidatorTest.class, "validatorState.json"),
            StandardCharsets.UTF_8);

    AssertionsForClassTypes.assertThat(data).isEqualTo(expected);
  }
}
